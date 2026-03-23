# WebSocket Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         FRONTEND (React)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      App.tsx                             │  │
│  │  ┌────────────────────────────────────────────────┐      │  │
│  │  │     WebSocketProvider (Context)                │      │  │
│  │  │  - Socket.IO Client Connection                 │      │  │
│  │  │  - Auto-reconnection                           │      │  │
│  │  │  - Token Authentication                        │      │  │
│  │  │  ┌──────────────────────────────────────────┐ │      │  │
│  │  │  │         Dashboard                        │ │      │  │
│  │  │  │  ┌────────────────┬──────────────────┐  │ │      │  │
│  │  │  │  │ Notifications  │    Messages      │  │ │      │  │
│  │  │  │  │                │                  │  │ │      │  │
│  │  │  │  │ • Real-time    │ • Chat UI       │  │ │      │  │
│  │  │  │  │   updates      │ • Send/receive  │  │ │      │  │
│  │  │  │  │ • No polling   │ • Typing        │  │ │      │  │
│  │  │  │  └────────────────┴──────────────────┘  │ │      │  │
│  │  │  └──────────────────────────────────────────┘ │      │  │
│  │  └────────────────────────────────────────────────┘      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↕                                    │
│                   Socket.IO Events                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                            ↕
                    WebSocket Connection
                  (ws://localhost:3000)
                            ↕
┌─────────────────────────────────────────────────────────────────┐
│                      BACKEND (Node.js)                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   index.ts (Main Server)                  │  │
│  │                                                            │  │
│  │  ┌─────────────────────┐    ┌────────────────────────┐   │  │
│  │  │   HTTP Server       │    │  Socket.IO Server      │   │  │
│  │  │   (Express)         │    │  (websocket.ts)        │   │  │
│  │  │                     │    │                        │   │  │
│  │  │  REST API:          │    │  • Authentication      │   │  │
│  │  │  • POST /login      │    │  • User rooms         │   │  │
│  │  │  • GET /reservations│◄───┼──• notifyUser()       │   │  │
│  │  │  • PATCH /status    │────┼─►• Event emitter      │   │  │
│  │  │                     │    │  • Connection tracking│   │  │
│  │  └─────────────────────┘    └────────────────────────┘   │  │
│  │              │                          │                 │  │
│  └──────────────┼──────────────────────────┼─────────────────┘  │
│                 ↓                          ↓                    │
│         ┌───────────────────────────────────────────┐          │
│         │          PostgreSQL Database               │          │
│         │  • users                                   │          │
│         │  • sessions (tokens)                       │          │
│         │  • reservations                            │          │
│         │  • dorms                                   │          │
│         └───────────────────────────────────────────┘          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════
                        EVENT FLOW DIAGRAM
═══════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────┐
│              RESERVATION UPDATE FLOW (Example)                   │
└─────────────────────────────────────────────────────────────────┘

   Frontend (Owner)                Backend                  Frontend (Tenant)
        │                              │                            │
        │  1. Approve Reservation      │                            │
        ├─────────────────────────────>│                            │
        │  PATCH /reservations/5/status│                            │
        │                              │                            │
        │                              │ 2. Update DB               │
        │                              ├───────────►[PostgreSQL]    │
        │                              │                            │
        │  3. Return Success           │                            │
        │<─────────────────────────────┤                            │
        │                              │                            │
        │                              │ 4. notifyUser(io, tenantId,│
        │                              │    'reservation_updated')  │
        │                              │                            │
        │                              │ 5. Emit to user:{tenantId} │
        │                              ├───────────────────────────>│
        │                              │    reservation_updated     │
        │                              │                            │
        │              6. Both frontends refresh their data         │
        │<──────────────────refresh────┼─────refresh────────────────┤
        │                              │                            │
        │  7. UI updates instantly     │        8. Notification     │
        │     (no polling delay!)      │           appears!         │
        ●                              │                            ●


═══════════════════════════════════════════════════════════════════
                     CHAT MESSAGE FLOW (Example)
═══════════════════════════════════════════════════════════════════

   Frontend (User A)               Backend                 Frontend (User B)
        │                              │                            │
        │  1. Type message             │                            │
        │  "Hello!"                    │                            │
        │                              │                            │
        │  2. sendMessage(userB, msg)  │                            │
        ├─────────────────────────────>│                            │
        │  emit: 'send_message'        │                            │
        │                              │                            │
        │                              │ 3. Receive & validate      │
        │                              │                            │
        │                              │ 4. Emit to user:{userB}    │
        │  5. Confirmation             ├───────────────────────────>│
        │<─────────────────────────────┤    emit: 'new_message'    │
        │  emit: 'message_sent'        │                            │
        │                              │                            │
        │  6. Message added to chat    │      7. Message appears    │
        │     (shows as "sent")        │         (shows as "new")   │
        ●                              │                            ●


═══════════════════════════════════════════════════════════════════
                    CONNECTION LIFECYCLE
═══════════════════════════════════════════════════════════════════

Frontend                                      Backend
   │                                             │
   │  1. User logs in                            │
   ├────────────────────────────────────────────>│
   │  POST /auth/login                           │
   │                                             │
   │  2. Receive token                           │
   │<────────────────────────────────────────────┤
   │  Store in localStorage                      │
   │                                             │
   │  3. Navigate to Dashboard                   │
   │  WebSocketProvider initializes              │
   │                                             │
   │  4. Connect with token                      │
   ├────────────────────────────────────────────>│
   │  io.connect({ auth: { token } })            │
   │                                             │
   │                                             │ 5. Validate token
   │                                             │    against DB
   │                                             │
   │  6. Connection accepted                     │
   │<────────────────────────────────────────────┤
   │  emit: 'connect'                            │
   │                                             │
   │                                             │ 7. Join room
   │                                             │    user:{userId}
   │                                             │
   │  8. Success notification                    │
   │  "Connected to real-time updates"           │
   │                                             │
   │  ──────── User active, events flowing ─────>│
   │  <────── Real-time updates ─────────────────│
   │                                             │
   │  9. User logs out or closes tab            │
   │  WebSocket disconnects                      │
   │                                             │
   ●                                             ● Remove from room


═══════════════════════════════════════════════════════════════════
                      ROOMS & NAMESPACES
═══════════════════════════════════════════════════════════════════

Socket.IO organizes connections using ROOMS:

┌────────────────────────────────────────────────────────────────┐
│                    Socket.IO Server                             │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Room: user:5                   Room: user:12                  │
│  ┌─────────────────┐            ┌─────────────────┐           │
│  │ Socket abc123   │            │ Socket xyz789   │           │
│  │ (Browser Tab 1) │            │ (Browser Tab 1) │           │
│  │                 │            │                 │           │
│  │ Socket def456   │            └─────────────────┘           │
│  │ (Browser Tab 2) │                                          │
│  │                 │            Room: user:23                 │
│  └─────────────────┘            ┌─────────────────┐           │
│                                  │ Socket qwe456   │           │
│                                  │ (Mobile App)    │           │
│                                  └─────────────────┘           │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

• Each user gets their own room: user:{userId}
• Multiple sockets (tabs/devices) can join same room
• Broadcast to room = all user's devices receive it


═══════════════════════════════════════════════════════════════════
                   FILE STRUCTURE REFERENCE
═══════════════════════════════════════════════════════════════════

DormEase/
│
├── Backend/
│   ├── src/
│   │   ├── index.ts           ← Modified (HTTP + WebSocket init)
│   │   ├── websocket.ts       ← NEW (WebSocket service)
│   │   └── db.ts              ← Existing (Database pool)
│   └── package.json           ← Updated (socket.io added)
│
├── src/
│   ├── contexts/
│   │   └── WebSocketContext.tsx  ← NEW (WebSocket client)
│   │
│   ├── App.tsx                ← Modified (WebSocketProvider)
│   ├── Notifications.tsx      ← Modified (Real-time updates)
│   ├── Messages.tsx           ← Rebuilt (Real-time chat)
│   └── main.tsx               ← Existing (Entry point)
│
└── package.json               ← Updated (socket.io-client added)


═══════════════════════════════════════════════════════════════════
                        KEY CONCEPTS
═══════════════════════════════════════════════════════════════════

1. PERSISTENT CONNECTION
   - Unlike HTTP (request → response → close)
   - WebSocket stays open (bidirectional)
   - Events flow both ways continuously

2. ROOMS (Namespaces)
   - Logical groups for sockets
   - user:5 = all connections for user ID 5
   - Efficient targeting (no broadcast spam)

3. EVENTS (Not REST)
   - Custom named events: 'send_message', 'notification'
   - Event-driven architecture
   - Both sides can emit and listen

4. AUTHENTICATION
   - Token sent during handshake (not per-request)
   - Validated once at connection time
   - Attached to socket for entire session

5. RECONNECTION
   - Socket.IO auto-reconnects on disconnect
   - Exponential backoff (1s, 2s, 4s...)
   - Seamless resume after network issues

═══════════════════════════════════════════════════════════════════
