import { Server as SocketIOServer, Socket } from "socket.io";
import { Server as HTTPServer } from "http";
import { pool } from "./db";

// FILE PATH: src/websocket.ts

interface AuthenticatedSocket extends Socket {
  userId?: number;
  userEmail?: string;
}

// Store active user connections
const userSockets = new Map<number, Set<string>>();

export function initializeWebSocket(httpServer: HTTPServer) {
  const io = new SocketIOServer(httpServer, {
    cors: {
      origin: (origin, callback) => {
        const allowed = ["http://localhost:5173", "http://localhost:5176"];
        // Allow whitelisted web origins OR no origin (Android / mobile apps)
        if (!origin || allowed.includes(origin)) {
          callback(null, true);
        } else {
          callback(new Error(`CORS blocked: ${origin}`));
        }
      },
      credentials: true,
    },
  });

  // Authentication middleware
  io.use(async (socket: AuthenticatedSocket, next) => {
    const token = socket.handshake.auth.token || socket.handshake.headers.authorization;

    if (!token) {
      return next(new Error("Authentication error: No token provided"));
    }

    try {
      const result = await pool.query(
        `SELECT user_id, email FROM sessions
         JOIN users ON sessions.user_id = users.id
         WHERE token = $1 AND expires_at > NOW()`,
        [token]
      );

      if (result.rows.length === 0) {
        return next(new Error("Authentication error: Invalid or expired token"));
      }

      socket.userId = result.rows[0].user_id;
      socket.userEmail = result.rows[0].email;
      next();
    } catch (error) {
      console.error("WebSocket auth error:", error);
      next(new Error("Authentication error"));
    }
  });

  io.on("connection", (socket: AuthenticatedSocket) => {
    const userId = socket.userId!;
    console.log(`User ${userId} connected via WebSocket (${socket.id})`);

    // Track user connection
    if (!userSockets.has(userId)) {
      userSockets.set(userId, new Set());
    }
    userSockets.get(userId)!.add(socket.id);

    // Join user's personal room
    socket.join(`user:${userId}`);

    // FIX: The `send_message` socket handler has been REMOVED.
    //
    // Previously this handler BOTH persisted the message to the DB AND emitted
    // `new_message` to the recipient. But the HTTP POST /messages/send endpoint
    // ALSO persists to DB AND calls notifyUser() to emit `new_message`.
    //
    // When mobile called SocketManager.sendMessage() + sendViaHttp() together,
    // the recipient received the message TWICE — once from the socket handler
    // and once from the HTTP endpoint's notifyUser() call.
    //
    // Now ALL sending goes through HTTP only:
    //   POST /messages/send  →  saves to DB  →  notifyUser(recipient only)
    //
    // The WebSocket connection is used exclusively for RECEIVING push events.

    // Handle typing indicator (kept — this is receive-only, no DB write)
    socket.on("typing", (data: { recipientId: number; isTyping: boolean }) => {
      io.to(`user:${data.recipientId}`).emit("user_typing", {
        userId,
        isTyping: data.isTyping,
      });
    });

    // Handle disconnect
    socket.on("disconnect", () => {
      console.log(`User ${userId} disconnected (${socket.id})`);

      const sockets = userSockets.get(userId);
      if (sockets) {
        sockets.delete(socket.id);
        if (sockets.size === 0) {
          userSockets.delete(userId);
        }
      }
    });
  });

  return io;
}

// Utility functions to emit events from API routes
export function getSocketIO(httpServer: HTTPServer): SocketIOServer | null {
  // @ts-ignore
  return httpServer._socketio || null;
}

export function notifyUser(io: SocketIOServer, userId: number, event: string, data: any) {
  // FIX: This only emits to the specified userId's room.
  // Always called with the RECIPIENT's userId from the HTTP handler — never the sender's.
  io.to(`user:${userId}`).emit(event, data);
}

export function notifyMultipleUsers(io: SocketIOServer, userIds: number[], event: string, data: any) {
  userIds.forEach(userId => {
    io.to(`user:${userId}`).emit(event, data);
  });
}

export { userSockets };