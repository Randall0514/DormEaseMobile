# WebSocket Implementation Guide for DormEase

## 🎯 Overview
This document describes the complete WebSocket implementation for real-time features in DormEase, including notifications and messaging.

---

## 📦 Installation Complete

### Backend Dependencies
- ✅ `socket.io` - WebSocket server library
- ✅ `@types/socket.io` - TypeScript types

### Frontend Dependencies
- ✅ `socket.io-client` - WebSocket client library

---

## 🏗️ Architecture

### Backend Structure

#### 1. **WebSocket Service** (`Backend/src/websocket.ts`)
Main WebSocket server implementation with:
- Authentication middleware (validates tokens from database)
- User connection tracking
- Event handlers for messages and typing indicators
- Utility functions to emit notifications

#### 2. **Server Integration** (`Backend/src/index.ts`)
- HTTP server created from Express app
- WebSocket initialized and attached to HTTP server
- WebSocket instance stored in Express app for route access

#### 3. **API Integration**
WebSocket notifications are sent from these endpoints:
- `PATCH /reservations/:id/status` - When owner approves/rejects
- `PATCH /reservations/:id/tenant-action` - When tenant accepts/cancels

### Frontend Structure

#### 1. **WebSocket Context** (`src/contexts/WebSocketContext.tsx`)
React context providing:
- Socket connection management
- Auto-reconnection logic
- Event subscription methods
- Message sending functionality
- Connection status

#### 2. **Connected Components**
- **Notifications.tsx** - Real-time reservation updates (removed polling)
- **Messages.tsx** - Real-time chat functionality
- **App.tsx** - Wraps Dashboard with WebSocket provider

---

## 🔌 WebSocket Events

### Client → Server

| Event | Data | Description |
|-------|------|-------------|
| `send_message` | `{ recipientId: number, message: string }` | Send chat message to another user |
| `typing` | `{ recipientId: number, isTyping: boolean }` | Send typing indicator |

### Server → Client

| Event | Data | Description |
|-------|------|-------------|
| `new_message` | `{ senderId, senderEmail, message, timestamp }` | Incoming chat message |
| `message_sent` | `{ recipientId, message, timestamp }` | Confirmation of sent message |
| `user_typing` | `{ userId, isTyping }` | Another user is typing |
| `notification` | `{ message, ...data }` | Generic notification |
| `reservation_updated` | `{ reservationId, status, message }` | Reservation status changed |
| `error` | `{ message }` | Error occurred |

---

## 🔐 Authentication

WebSocket connections require authentication:

1. Token is retrieved from localStorage (`dormease_token`)
2. Sent in connection handshake: `auth: { token }`
3. Server validates token against database sessions
4. Invalid tokens are rejected with error

---

## 💻 Usage Examples

### Frontend - Send a Message
```tsx
import { useWebSocket } from './contexts/WebSocketContext';

function MyComponent() {
  const { sendMessage, isConnected } = useWebSocket();
  
  const handleSend = () => {
    if (isConnected) {
      sendMessage(recipientUserId, 'Hello!');
    }
  };
}
```

### Frontend - Listen for Notifications
```tsx
import { useWebSocket } from './contexts/WebSocketContext';

function MyComponent() {
  const { onNotification, offNotification } = useWebSocket();
  
  useEffect(() => {
    const handler = (data) => {
      console.log('Notification:', data);
      // Update UI
    };
    
    onNotification(handler);
    return () => offNotification(handler);
  }, [onNotification, offNotification]);
}
```

### Backend - Send Notification to User
```typescript
import { notifyUser } from './websocket';

// Inside an API route
const io = req.app.get('io');
if (io) {
  notifyUser(io, userId, 'reservation_updated', {
    reservationId: 123,
    status: 'approved',
    message: 'Your reservation was approved!'
  });
}
```

### Backend - Notify Multiple Users
```typescript
import { notifyMultipleUsers } from './websocket';

const io = req.app.get('io');
if (io) {
  notifyMultipleUsers(io, [userId1, userId2], 'notification', {
    message: 'New announcement!'
  });
}
```

---

## 🚀 Starting the System

### Backend
```bash
cd Backend
npm run dev
```

Expected output:
```
🚀 Server running at http://192.168.68.124:3000
🔌 WebSocket server is ready
```

### Frontend
```bash
npm run dev
```

The frontend will automatically connect to WebSocket when:
- User is logged in (has valid token)
- On the Dashboard view
- Server is running

---

## 🔄 Connection Flow

1. User logs in → Token stored in localStorage
2. App navigates to Dashboard
3. WebSocketProvider initializes
4. Socket.IO connects with token
5. Backend validates token
6. Connection established
7. User joins personal room: `user:{userId}`
8. Ready to receive real-time updates

---

## 🐛 Debugging

### Check Connection Status
In browser console:
```javascript
// Should show true when connected
console.log('WebSocket connected:', window.socketConnected);
```

### Backend Logs
```
✅ User 123 connected via WebSocket (socket-id-abc)
❌ User 123 disconnected (socket-id-abc)
```

### Frontend Connection Messages
- "Connected to real-time updates" (green notification)
- "Reconnected to server" (blue notification)
- "Failed to reconnect. Please refresh the page." (red notification)

---

## ⚙️ Configuration

### Backend Configuration
**File:** `Backend/src/websocket.ts`

```typescript
// CORS settings - add allowed origins
const io = new SocketIOServer(httpServer, {
  cors: {
    origin: ['http://localhost:5173', 'http://localhost:5176'],
    credentials: true,
  },
});
```

### Frontend Configuration
**File:** `src/contexts/WebSocketContext.tsx`

```typescript
// WebSocket server URL
const SOCKET_URL = 'http://localhost:3000';

// Reconnection settings
reconnection: true,
reconnectionDelay: 1000,
reconnectionDelayMax: 5000,
reconnectionAttempts: 5,
```

---

## 📋 Features Implemented

### ✅ Real-time Notifications
- Reservation status changes (approved/rejected)
- Tenant actions (accepted/cancelled)
- Replaced polling with WebSocket events
- Instant UI updates

### ✅ Real-time Messaging
- One-on-one chat functionality
- Typing indicators support
- Message delivery confirmation
- Conversation management

### ✅ Connection Management
- Auto-reconnection on disconnect
- Token-based authentication
- Connection status indicators
- Error handling and user feedback

---

## 🔧 Future Enhancements

### Potential Additions
1. **Message Persistence** - Store messages in database
2. **Read Receipts** - Show when messages are read
3. **Online Status** - Show which users are online
4. **Group Chat** - Support for multiple participants
5. **Push Notifications** - Browser notifications for background updates
6. **File Sharing** - Send images/documents in chat
7. **Video/Audio Calls** - WebRTC integration

### Database Schema for Messages (Future)
```sql
CREATE TABLE messages (
  id SERIAL PRIMARY KEY,
  sender_id INTEGER REFERENCES users(id),
  recipient_id INTEGER REFERENCES users(id),
  message TEXT NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_messages_recipient ON messages(recipient_id);
CREATE INDEX idx_messages_sender ON messages(sender_id);
```

---

## 📝 Notes

### Pre-existing Errors
The backend has TypeScript configuration warnings related to CommonJS/ESM modules. These are **not caused** by the WebSocket implementation and existed before:
- The project uses `"type": "commonjs"` in package.json
- TypeScript uses ES6 imports
- This works at runtime but shows warnings in IDE
- To fix: Either change to `"type": "module"` or adjust TypeScript config

### Performance
- Each user connection uses minimal resources
- Socket.IO handles reconnection automatically
- Personal rooms prevent message broadcast overhead
- Scalable for thousands of concurrent connections

### Security
- All WebSocket connections are authenticated
- Tokens validated against database on connect
- Users can only receive their own notifications
- Chat messages verify sender identity

---

## 🎉 Implementation Complete!

All WebSocket functionality has been properly integrated into your DormEase system. The system now supports:
- ✅ Real-time notifications
- ✅ Real-time messaging
- ✅ Secure authentication
- ✅ Auto-reconnection
- ✅ Error handling

**Start both servers and test the real-time features!**
