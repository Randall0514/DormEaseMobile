# WebSocket Configuration Reference

## Environment Variables

### Backend (.env file)

```bash
# Server Configuration
PORT=3000
NODE_ENV=development

# Database (existing)
DATABASE_URL=postgresql://user:password@localhost:5432/dormease

# Session/Auth (existing)
SESSION_MINUTES=10080

# WebSocket Configuration (optional)
# If not set, defaults are used

# CORS Origins (hardcoded in code, but you can add env vars)
# ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5176

# WebSocket Server Options
# WEBSOCKET_PING_TIMEOUT=60000      # 60 seconds
# WEBSOCKET_PING_INTERVAL=25000     # 25 seconds
```

### Frontend (environment or constants)

Currently hardcoded in files, but can be moved to environment:

**File: `src/contexts/WebSocketContext.tsx`**
```typescript
// Current:
const SOCKET_URL = 'http://localhost:3000';
const AUTH_TOKEN_KEY = 'dormease_token';

// Can be changed to:
const SOCKET_URL = import.meta.env.VITE_SOCKET_URL || 'http://localhost:3000';
const AUTH_TOKEN_KEY = import.meta.env.VITE_AUTH_TOKEN_KEY || 'dormease_token';
```

**Create `.env` file in root:**
```bash
VITE_SOCKET_URL=http://localhost:3000
VITE_AUTH_TOKEN_KEY=dormease_token
```

---

## Configuration Files

### Backend WebSocket Settings

**File: `Backend/src/websocket.ts`**

```typescript
// CORS Configuration
export function initializeWebSocket(httpServer: HTTPServer) {
  const io = new SocketIOServer(httpServer, {
    cors: {
      origin: ['http://localhost:5173', 'http://localhost:5176'],
      credentials: true,
    },
  });
  // ...
}
```

**To allow more origins:**
```typescript
cors: {
  origin: [
    'http://localhost:5173',
    'http://localhost:5176', 
    'http://192.168.1.100:5173',  // Add local network IP
    'https://yourdomain.com'       // Add production domain
  ],
  credentials: true,
},
```

**Production configuration:**
```typescript
cors: {
  origin: process.env.ALLOWED_ORIGINS?.split(',') || '*',
  credentials: true,
},
```

---

### Frontend WebSocket Settings

**File: `src/contexts/WebSocketContext.tsx`**

```typescript
// Connection Configuration
const newSocket = io(SOCKET_URL, {
  auth: { token },
  autoConnect: true,
  reconnection: true,
  reconnectionDelay: 1000,        // Start with 1 second
  reconnectionDelayMax: 5000,     // Max 5 seconds between attempts
  reconnectionAttempts: 5,        // Try 5 times before giving up
});
```

**Aggressive reconnection (for unstable networks):**
```typescript
reconnection: true,
reconnectionDelay: 500,           // 0.5 seconds
reconnectionDelayMax: 3000,       // Max 3 seconds
reconnectionAttempts: 10,         // Try 10 times
```

**Conservative (for stable networks):**
```typescript
reconnection: true,
reconnectionDelay: 2000,          // 2 seconds
reconnectionDelayMax: 10000,      // Max 10 seconds
reconnectionAttempts: 3,          // Try only 3 times
```

**Production (disable auto-connect, manual control):**
```typescript
autoConnect: false,               // Don't connect automatically
reconnection: true,
reconnectionDelay: 2000,
reconnectionDelayMax: 10000,
reconnectionAttempts: 5,
```

Then manually connect:
```typescript
useEffect(() => {
  if (token && shouldConnect) {
    newSocket.connect();
  }
}, [token, shouldConnect]);
```

---

## Server Configuration

### Backend Server Setup

**File: `Backend/src/index.ts`**

```typescript
const PORT = process.env.PORT || 3000;

// CORS for HTTP
const allowedOrigins = ['http://localhost:5173', 'http://localhost:5176'];
app.use(cors({
  origin: (origin, callback) => {
    if (!origin) return callback(null, true);
    if (allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
}));
```

**Production CORS:**
```typescript
const allowedOrigins = process.env.ALLOWED_ORIGINS?.split(',') || [
  'http://localhost:5173',
  'http://localhost:5176'
];
```

---

## Advanced Socket.IO Options

### Server Options (Backend)

```typescript
const io = new SocketIOServer(httpServer, {
  // CORS
  cors: {
    origin: ['http://localhost:5173'],
    credentials: true,
  },
  
  // Connection timeout
  connectTimeout: 45000,           // 45 seconds to connect
  
  // Ping/pong (keep-alive)
  pingTimeout: 60000,              // 60 seconds without pong = disconnect
  pingInterval: 25000,             // Send ping every 25 seconds
  
  // Max payload size
  maxHttpBufferSize: 1e6,          // 1MB (default)
  
  // Allow upgrades (HTTP long-polling → WebSocket)
  allowUpgrades: true,
  
  // Transports (prefer WebSocket)
  transports: ['websocket', 'polling'],
  
  // Compression
  perMessageDeflate: {
    threshold: 1024,               // Compress messages > 1KB
  },
  
  // Cookie (for session management)
  cookie: false,                   // Don't use cookies (we use tokens)
});
```

### Client Options (Frontend)

```typescript
const socket = io(SOCKET_URL, {
  // Authentication
  auth: { token },
  
  // Auto-connect
  autoConnect: true,
  
  // Reconnection
  reconnection: true,
  reconnectionDelay: 1000,
  reconnectionDelayMax: 5000,
  reconnectionAttempts: 5,
  
  // Timeout
  timeout: 20000,                  // Connection timeout: 20 seconds
  
  // Transports (try WebSocket first, fallback to polling)
  transports: ['websocket', 'polling'],
  
  // Upgrade (start with polling, upgrade to WebSocket)
  upgrade: true,
  
  // Force new connection
  forceNew: false,                 // Reuse existing connection
  
  // Multiplexing
  multiplex: true,                 // Use single connection for multiple namespaces
  
  // Reconnection on error
  reconnectionErrorDelay: 1000,
});
```

---

## Performance Tuning

### For High Traffic (Many Users)

**Backend:**
```typescript
// Increase max listeners
io.sockets.setMaxListeners(0);

// Use Redis adapter for horizontal scaling
import { createAdapter } from '@socket.io/redis-adapter';
import { createClient } from 'redis';

const pubClient = createClient({ url: 'redis://localhost:6379' });
const subClient = pubClient.duplicate();

Promise.all([pubClient.connect(), subClient.connect()]).then(() => {
  io.adapter(createAdapter(pubClient, subClient));
});
```

### For Low Bandwidth (Mobile Networks)

**Both:**
```typescript
// Server
perMessageDeflate: true,          // Enable compression
maxHttpBufferSize: 5e5,           // Reduce buffer (500KB)

// Client
transports: ['polling', 'websocket'],  // Start with polling (more reliable)
upgrade: true,                    // Upgrade when stable
```

### For Real-time Gaming (Low Latency)

**Both:**
```typescript
// Server
pingInterval: 10000,              // Ping every 10 seconds
pingTimeout: 5000,                // Disconnect after 5s without pong

// Client
reconnectionDelay: 100,           // Reconnect quickly (100ms)
reconnectionDelayMax: 1000,       // Max 1 second
transports: ['websocket'],        // WebSocket only (no polling)
upgrade: false,
```

---

## Security Configuration

### Token Configuration

**Backend:**
```typescript
// Token validation
io.use(async (socket, next) => {
  const token = socket.handshake.auth.token;
  
  if (!token) {
    return next(new Error('Authentication required'));
  }
  
  // Add rate limiting
  const attempts = rateLimiter.get(socket.handshake.address);
  if (attempts > 10) {
    return next(new Error('Too many attempts'));
  }
  
  // Validate token
  // ...
});
```

### Rate Limiting

**Backend:**
```typescript
import rateLimit from 'express-rate-limit';

const wsRateLimiter = new Map();

io.use((socket, next) => {
  const ip = socket.handshake.address;
  const now = Date.now();
  const windowMs = 60000; // 1 minute
  
  if (!wsRateLimiter.has(ip)) {
    wsRateLimiter.set(ip, { count: 0, resetTime: now + windowMs });
  }
  
  const record = wsRateLimiter.get(ip);
  
  if (now > record.resetTime) {
    record.count = 0;
    record.resetTime = now + windowMs;
  }
  
  if (record.count > 100) { // 100 messages per minute
    return next(new Error('Rate limit exceeded'));
  }
  
  record.count++;
  next();
});
```

---

## Deployment Configuration

### Production Backend (.env)

```bash
NODE_ENV=production
PORT=3000

# Database
DATABASE_URL=postgresql://user:pass@prod-db.com:5432/dormease

# CORS
ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com

# WebSocket (optional)
WEBSOCKET_PING_TIMEOUT=60000
WEBSOCKET_PING_INTERVAL=25000
```

### Production Frontend (.env.production)

```bash
VITE_API_BASE=https://api.yourdomain.com
VITE_SOCKET_URL=https://api.yourdomain.com
VITE_AUTH_TOKEN_KEY=dormease_token
```

### Nginx Configuration (for WebSocket)

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        
        # WebSocket support
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
    }
}
```

### Docker Configuration

**Backend Dockerfile:**
```dockerfile
FROM node:18

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

COPY . .

# Expose both HTTP and WebSocket
EXPOSE 3000

CMD ["npm", "start"]
```

**docker-compose.yml:**
```yaml
version: '3.8'

services:
  backend:
    build: ./Backend
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - PORT=3000
      - DATABASE_URL=postgresql://user:pass@db:5432/dormease
      - ALLOWED_ORIGINS=http://localhost:5173
    depends_on:
      - db
  
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: dormease
      POSTGRES_USER: user
      POSTGRES_PASSWORD: pass
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

---

## Monitoring & Debugging

### Enable Debug Logs

**Backend:**
```bash
# In terminal
DEBUG=socket.io* npm run dev
```

**Frontend:**
```typescript
// Add before io() call
import { enableDebug } from 'socket.io-client';
enableDebug('socket.io-client:*');
```

### Connection Metrics

**Backend:**
```typescript
// Track connections
let connectionCount = 0;

io.on('connection', (socket) => {
  connectionCount++;
  console.log(`Total connections: ${connectionCount}`);
  
  socket.on('disconnect', () => {
    connectionCount--;
    console.log(`Total connections: ${connectionCount}`);
  });
});

// Endpoint to check status
app.get('/socket-status', (req, res) => {
  res.json({
    totalConnections: connectionCount,
    connectedUsers: userSockets.size,
  });
});
```

---

## Quick Reference

| Setting | Backend File | Frontend File |
|---------|--------------|---------------|
| CORS Origins | `websocket.ts`, `index.ts` | N/A |
| Socket URL | N/A | `WebSocketContext.tsx` |
| Reconnection | N/A | `WebSocketContext.tsx` |
| Ping/Pong | `websocket.ts` | N/A |
| Auth Token | `websocket.ts` | `WebSocketContext.tsx` |
| Server Port | `index.ts` | N/A |

---

**All configurations are optional and use sensible defaults!**
