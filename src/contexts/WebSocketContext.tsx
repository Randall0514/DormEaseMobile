import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { message as antdMessage } from 'antd';

interface WebSocketContextType {
  socket: Socket | null;
  isConnected: boolean;
  sendMessage: (recipientId: number, message: string) => void;
  onNewMessage: (callback: (data: any) => void) => void;
  onMessageSent: (callback: (data: any) => void) => void;
  onNotification: (callback: (data: any) => void) => void;
  offNewMessage: (callback: (data: any) => void) => void;
  offMessageSent: (callback: (data: any) => void) => void;
  offNotification: (callback: (data: any) => void) => void;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

const SOCKET_URL = 'http://localhost:3000';
const AUTH_TOKEN_KEY = 'dormease_token';

interface Props {
  children: React.ReactNode;
}

export const WebSocketProvider: React.FC<Props> = ({ children }) => {
  const [socket, setSocket] = useState<Socket | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const socketRef = useRef<Socket | null>(null);

  // Ref-based callback registries — ONE socket listener per event,
  // callbacks are managed via Sets to prevent duplicate handlers.
  const newMessageCallbacks = useRef<Set<(data: any) => void>>(new Set());
  const messageSentCallbacks = useRef<Set<(data: any) => void>>(new Set());
  const notificationCallbacks = useRef<Set<(data: any) => void>>(new Set());

  useEffect(() => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    
    if (!token) {
      console.log('⚠️ No auth token, skipping WebSocket connection');
      return;
    }

    // Initialize Socket.IO client
    const newSocket = io(SOCKET_URL, {
      auth: { token },
      autoConnect: true,
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 5000,
      reconnectionAttempts: Number.MAX_SAFE_INTEGER,
      transports: ['websocket', 'polling'],
    });

    socketRef.current = newSocket;

    newSocket.on('connect', () => {
      console.log('✅ WebSocket connected');
      setIsConnected(true);
      antdMessage.success('Connected to real-time updates', 2);
    });

    newSocket.on('disconnect', (reason) => {
      console.log('❌ WebSocket disconnected:', reason);
      setIsConnected(false);
    });

    newSocket.on('connect_error', (error) => {
      console.error('🔴 WebSocket connection error:', error.message);
      setIsConnected(false);
    });

    newSocket.on('error', (error) => {
      console.error('🔴 WebSocket error:', error);
      antdMessage.error(error.message || 'WebSocket error occurred');
    });

    // Handle reconnection
    newSocket.on('reconnect', (attemptNumber) => {
      console.log(`🔄 Reconnected after ${attemptNumber} attempts`);
      antdMessage.info('Reconnected to server', 2);
    });

    newSocket.on('reconnect_attempt', (attemptNumber) => {
      console.log(`🔄 Reconnection attempt ${attemptNumber}`);
    });

    newSocket.on('reconnect_failed', () => {
      console.error('❌ Failed to reconnect to server');
      antdMessage.error('Failed to reconnect. Please refresh the page.');
    });

    const reconnectIfNeeded = () => {
      const latestToken = localStorage.getItem(AUTH_TOKEN_KEY);
      if (!latestToken || newSocket.connected) {
        return;
      }

      newSocket.auth = { token: latestToken };
      newSocket.connect();
    };

    const handleWindowFocus = () => {
      reconnectIfNeeded();
    };

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        reconnectIfNeeded();
      }
    };

    window.addEventListener('focus', handleWindowFocus);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Register exactly ONE listener per event on the socket.
    // Each listener fans out to all registered callbacks.
    newSocket.on('new_message', (data: any) => {
      newMessageCallbacks.current.forEach((cb) => cb(data));
    });

    newSocket.on('message_sent', (data: any) => {
      messageSentCallbacks.current.forEach((cb) => cb(data));
    });

    newSocket.on('notification', (data: any) => {
      notificationCallbacks.current.forEach((cb) => cb(data));
    });

    newSocket.on('reservation_updated', (data: any) => {
      notificationCallbacks.current.forEach((cb) => cb(data));
    });

    setSocket(newSocket);

    return () => {
      console.log('🔌 Cleaning up WebSocket connection');
      window.removeEventListener('focus', handleWindowFocus);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      newSocket.close();
    };
  }, []);

  const sendMessage = useCallback(
    (recipientId: number, message: string) => {
      const s = socketRef.current;
      if (s && s.connected) {
        s.emit('send_message', { recipientId, message });
      } else {
        antdMessage.error('Not connected to server');
      }
    },
    []
  );

  // Subscribe / unsubscribe callbacks via ref Sets.
  // These are stable references — no dependency on `socket` state.
  const onNewMessage = useCallback((callback: (data: any) => void) => {
    newMessageCallbacks.current.add(callback);
  }, []);

  const offNewMessage = useCallback((callback: (data: any) => void) => {
    newMessageCallbacks.current.delete(callback);
  }, []);

  const onMessageSent = useCallback((callback: (data: any) => void) => {
    messageSentCallbacks.current.add(callback);
  }, []);

  const offMessageSent = useCallback((callback: (data: any) => void) => {
    messageSentCallbacks.current.delete(callback);
  }, []);

  const onNotification = useCallback((callback: (data: any) => void) => {
    notificationCallbacks.current.add(callback);
  }, []);

  const offNotification = useCallback((callback: (data: any) => void) => {
    notificationCallbacks.current.delete(callback);
  }, []);

  const value: WebSocketContextType = {
    socket,
    isConnected,
    sendMessage,
    onNewMessage,
    onMessageSent,
    onNotification,
    offNewMessage,
    offMessageSent,
    offNotification,
  };

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = (): WebSocketContextType => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
};

export default WebSocketContext;
