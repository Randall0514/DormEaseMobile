import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Card, Typography, Empty, Grid, List, Input, Button, Avatar, Space, Badge, Modal, message as antdMessage } from 'antd';
import { SendOutlined, ArrowLeftOutlined, DeleteOutlined, CheckOutlined } from '@ant-design/icons';
import { useWebSocket } from './contexts/WebSocketContext';

const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

const API_BASE = 'http://localhost:3000';
const AUTH_TOKEN_KEY = 'dormease_token';

interface MessageItem {
  id: string | number;
  senderId: number;
  recipientId: number;
  text: string;
  timestamp: string;
  isMine: boolean;
}

interface ConversationItem {
  userId: number;
  userName: string;
  preview: string;
  unreadCount: number;
  updatedAt: string;
}

interface AuthMeResponse {
  user: {
    id: number;
  };
}

interface ConversationSummaryRow {
  id: number;
  full_name?: string;
  username?: string;
  relation?: 'tenant' | 'owner';
  last_message?: string;
  last_message_at?: string;
  last_sender_id?: number;
}

interface MessageHistoryRow {
  id: number;
  sender_id: number;
  recipient_id: number;
  message: string;
  created_at: string;
}

type Props = {
  onNavigate?: (section: string) => void;
  onUnreadCountChange?: (count: number) => void;
  conversationUnreadCounts?: Record<number, number>;
  onConversationUnreadCountsChange?: (counts: Record<number, number>) => void;
};

const Messages: React.FC<Props> = ({
  onUnreadCountChange,
  conversationUnreadCounts = {},
  onConversationUnreadCountsChange,
}) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  // FIX: We no longer need onMessageSent / offMessageSent since the sender
  // uses optimistic updates only and the server never echoes back to sender.
  const { onNewMessage, offNewMessage, isConnected } = useWebSocket();
  const isDarkMode = document.body.classList.contains('dark-mode');

  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [messagesByUser, setMessagesByUser] = useState<Record<number, MessageItem[]>>({});
  const [draftMessage, setDraftMessage] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [loadingHistoryByUser, setLoadingHistoryByUser] = useState<Record<number, boolean>>({});
  const [deletingConversationId, setDeletingConversationId] = useState<number | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const loadedHistoryRef = useRef<Record<number, boolean>>({});
  const currentUserIdRef = useRef<number | null>(null);
  const selectedConversationIdRef = useRef<number | null>(null);

  // Keep refs in sync for use inside stable callbacks
  useEffect(() => { currentUserIdRef.current = currentUserId; }, [currentUserId]);
  useEffect(() => { selectedConversationIdRef.current = selectedConversationId; }, [selectedConversationId]);

  const selectedConversation = useMemo(
    () => conversations.find((c) => c.userId === selectedConversationId) ?? null,
    [conversations, selectedConversationId]
  );

  const selectedMessages = useMemo(() => {
    if (!selectedConversationId) return [];
    const messages = messagesByUser[selectedConversationId] ?? [];
    // Deduplicate by ID
    const seen = new Set<string | number>();
    return messages.filter(msg => {
      if (seen.has(msg.id)) return false;
      seen.add(msg.id);
      return true;
    });
  }, [selectedConversationId, messagesByUser]);

  // Notify parent of total unread count
  useEffect(() => {
    const total = conversations.reduce((sum, conv) => sum + conv.unreadCount, 0);
    onUnreadCountChange?.(total);
  }, [conversations, onUnreadCountChange]);

  const filteredConversations = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    const sorted = [...conversations].sort(
      (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    );

    // Deduplicate by userId
    const seen = new Set<number>();
    const deduped = sorted.filter(conv => {
      if (seen.has(conv.userId)) return false;
      seen.add(conv.userId);
      return true;
    });

    if (!keyword) return deduped;
    return deduped.filter((c) =>
      c.userName.toLowerCase().includes(keyword) || c.preview.toLowerCase().includes(keyword)
    );
  }, [conversations, searchTerm]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [selectedMessages]);

  const getDisplayName = (user: { id: number; full_name?: string; username?: string; relation?: 'tenant' | 'owner' }) => {
    const base = user.full_name || user.username || `User ${user.id}`;
    if (user.relation === 'tenant') return `${base} (Tenant)`;
    if (user.relation === 'owner') return `${base} (Owner)`;
    return base;
  };

  useEffect(() => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) {
      setLoadingContacts(false);
      return;
    }

    const loadConversations = async () => {
      try {
        setLoadingContacts(true);
        const [meRes, usersRes] = await Promise.all([
          fetch(`${API_BASE}/auth/me`, { headers: { Authorization: `Bearer ${token}` } }),
          fetch(`${API_BASE}/messages/conversations`, { headers: { Authorization: `Bearer ${token}` } }),
        ]);

        if (!meRes.ok || !usersRes.ok) throw new Error('Failed to load contacts');

        const meData: AuthMeResponse = await meRes.json();
        const usersData: ConversationSummaryRow[] = await usersRes.json();
        const myId = Number(meData.user.id);

        setCurrentUserId(myId);

        // Deduplicate users
        const uniqueUsers = Array.from(
          new Map(
            usersData
              .filter((u) => Number(u.id) !== myId)
              .map(u => [Number(u.id), u])
          ).values()
        );

        setConversations(
          uniqueUsers.map((user) => {
            const userId = Number(user.id);
            return {
              userId,
              userName: getDisplayName(user),
              preview: user.last_message
                ? Number(user.last_sender_id) === myId
                  ? `You: ${user.last_message}`
                  : user.last_message
                : 'Start a conversation',
              unreadCount: conversationUnreadCounts[userId] || 0,
              updatedAt: user.last_message_at || new Date(0).toISOString(),
            };
          })
        );
      } catch {
        antdMessage.error('Unable to load users for messaging.');
      } finally {
        setLoadingContacts(false);
      }
    };

    loadConversations();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sync local conversations with global unread counts
  useEffect(() => {
    setConversations((prev) =>
      prev.map((conv) => ({
        ...conv,
        unreadCount: conversationUnreadCounts[conv.userId] || 0,
      }))
    );
  }, [conversationUnreadCounts]);

  // FIX: Single WebSocket handler — only processes INCOMING messages from OTHER people.
  // Our own sent messages are shown via optimistic updates in handleSendMessage().
  // We never add our own messages here, so no duplicates.
  useEffect(() => {
    const handleNewMessage = (data: any) => {
      const myId = currentUserIdRef.current;
      if (!myId) return;

      const senderId = Number(data?.senderId);
      const incomingText = String(data?.message ?? '').trim();
      if (!senderId || !incomingText) return;

      // FIX: If this event is about OUR OWN message being echoed back, ignore it.
      // The server should only push `new_message` to the recipient, but guard here too.
      if (senderId === myId) return;

      const incomingTimestamp = data?.timestamp || new Date().toISOString();
      const senderName = data?.senderEmail || `User ${senderId}`;
      const incomingId = data?.id ?? `ws-${Date.now()}-${Math.random()}`;

      const incoming: MessageItem = {
        id: incomingId,
        senderId,
        recipientId: myId,
        text: incomingText,
        timestamp: incomingTimestamp,
        isMine: false,
      };

      setMessagesByUser((prev) => {
        const existing = prev[senderId] ?? [];

        // Deduplicate by ID
        if (existing.some(m => m.id === incoming.id)) return prev;

        // Deduplicate by content+time window (2 seconds) to catch any edge cases
        const isDuplicate = existing.some(m =>
          !m.isMine &&
          m.text === incomingText &&
          Math.abs(new Date(m.timestamp).getTime() - new Date(incomingTimestamp).getTime()) < 2000
        );
        if (isDuplicate) return prev;

        return { ...prev, [senderId]: [...existing, incoming] };
      });

      setConversations((prev) => {
        const exists = prev.find((c) => c.userId === senderId);
        if (!exists) {
          return [
            {
              userId: senderId,
              userName: senderName,
              preview: incomingText,
              unreadCount: 1,
              updatedAt: incomingTimestamp,
            },
            ...prev,
          ];
        }
        return prev.map((c) =>
          c.userId === senderId
            ? { ...c, preview: incomingText, updatedAt: incomingTimestamp }
            : c
        );
      });

      if (selectedConversationIdRef.current !== senderId) {
        antdMessage.info(`New message from ${senderName}`);
      }
    };

    onNewMessage(handleNewMessage);
    return () => { offNewMessage(handleNewMessage); };
  }, [onNewMessage, offNewMessage]);

  const getAvatarLabel = (name: string) => {
    const cleanName = name.trim();
    return cleanName.length ? cleanName.charAt(0).toUpperCase() : 'U';
  };

  const formatTime = (iso: string) => {
    if (!iso || new Date(iso).getTime() === 0) return '';
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const loadConversationHistory = async (userId: number) => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token || loadedHistoryRef.current[userId]) return;

    setLoadingHistoryByUser((prev) => ({ ...prev, [userId]: true }));

    try {
      const res = await fetch(`${API_BASE}/messages/${userId}/history`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to load history');

      const rows: MessageHistoryRow[] = await res.json();
      const history: MessageItem[] = rows.map((row) => ({
        id: row.id,
        senderId: Number(row.sender_id),
        recipientId: Number(row.recipient_id),
        text: row.message,
        timestamp: row.created_at,
        isMine: Number(row.sender_id) === currentUserId,
      }));

      setMessagesByUser((prev) => {
        const existing = prev[userId] ?? [];
        const historyIds = new Set(history.map(h => h.id));

        // Remove any optimistic/temp messages that now have a real counterpart
        const filteredExisting = existing.filter(msg => {
          if (!String(msg.id).startsWith('temp-')) return !historyIds.has(msg.id);
          // Keep temp only if no matching real message was found
          return !history.some(h =>
            h.text === msg.text &&
            h.senderId === msg.senderId &&
            Math.abs(new Date(h.timestamp).getTime() - new Date(msg.timestamp).getTime()) < 10000
          );
        });

        // Merge and sort
        const combined = [...filteredExisting, ...history];
        combined.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

        // Final dedup by ID
        const seen = new Set<string | number>();
        return {
          ...prev,
          [userId]: combined.filter(m => {
            if (seen.has(m.id)) return false;
            seen.add(m.id);
            return true;
          }),
        };
      });

      loadedHistoryRef.current[userId] = true;
    } catch {
      antdMessage.error('Unable to load conversation history.');
    } finally {
      setLoadingHistoryByUser((prev) => ({ ...prev, [userId]: false }));
    }
  };

  const openConversation = (userId: number) => {
    setSelectedConversationId(userId);
    void loadConversationHistory(userId);
  };

  const markAsRead = () => {
    if (!selectedConversationId) return;
    if (onConversationUnreadCountsChange) {
      onConversationUnreadCountsChange({ ...conversationUnreadCounts, [selectedConversationId]: 0 });
    }
    setConversations((prev) =>
      prev.map((c) => c.userId === selectedConversationId ? { ...c, unreadCount: 0 } : c)
    );
    antdMessage.success('Marked as read');
  };

  const handleDeleteConversation = async () => {
    if (!selectedConversationId || !selectedConversation) return;

    const confirmDelete = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: 'Delete conversation?',
        content: (
          <div>
            <p>This will remove your chat with <strong>{selectedConversation.userName}</strong>.</p>
            <Text type="secondary">This action cannot be undone from your side.</Text>
          </div>
        ),
        okText: 'Delete',
        okButtonProps: { danger: true },
        cancelText: 'Cancel',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

    if (!confirmDelete) return;

    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) { antdMessage.error('You are not authenticated.'); return; }

    try {
      setDeletingConversationId(selectedConversationId);
      const res = await fetch(`${API_BASE}/messages/${selectedConversationId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Failed to delete conversation');
      }
      const deletedId = selectedConversationId;
      setConversations((prev) => prev.filter((c) => c.userId !== deletedId));
      setMessagesByUser((prev) => { const next = { ...prev }; delete next[deletedId]; return next; });
      delete loadedHistoryRef.current[deletedId];
      setSelectedConversationId(null);
      antdMessage.success('Conversation deleted.');
    } catch (err: any) {
      antdMessage.error(err?.message || 'Failed to delete conversation');
    } finally {
      setDeletingConversationId(null);
    }
  };

  const handleSendMessage = async () => {
    if (!selectedConversationId || !draftMessage.trim()) return;

    const trimmed = draftMessage.trim();
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) { antdMessage.error('Not authenticated'); return; }

    const outgoingTimestamp = new Date().toISOString();
    // Use a unique temp ID
    const tempId = `temp-${Date.now()}-${Math.random()}`;

    // FIX: Optimistically show the message immediately with a temp ID
    const outgoing: MessageItem = {
      id: tempId,
      senderId: currentUserId ?? 0,
      recipientId: selectedConversationId,
      text: trimmed,
      timestamp: outgoingTimestamp,
      isMine: true,
    };

    setMessagesByUser((prev) => ({
      ...prev,
      [selectedConversationId]: [...(prev[selectedConversationId] ?? []), outgoing],
    }));
    loadedHistoryRef.current[selectedConversationId] = true;

    setConversations((prev) =>
      prev.map((c) =>
        c.userId === selectedConversationId
          ? { ...c, preview: `You: ${trimmed}`, updatedAt: outgoingTimestamp, unreadCount: 0 }
          : c
      )
    );

    if (onConversationUnreadCountsChange) {
      onConversationUnreadCountsChange({ ...conversationUnreadCounts, [selectedConversationId]: 0 });
    }

    setDraftMessage('');

    // FIX: Send ONLY via HTTP. The server saves + pushes `new_message` to the RECIPIENT only.
    // We do NOT use the WebSocket sendMessage() here — that would cause the server to
    // also emit an event that ends up being processed as a second incoming message.
    try {
      const res = await fetch(`${API_BASE}/messages/send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ recipientId: selectedConversationId, message: trimmed }),
      });

      if (res.ok) {
        const payload = await res.json().catch(() => ({}));
        const savedId = payload?.data?.id;

        // FIX: Replace the temp message with the real DB ID so future dedup works correctly
        if (savedId) {
          setMessagesByUser((prev) => {
            const msgs = prev[selectedConversationId] ?? [];
            const updated = msgs.map(m => m.id === tempId ? { ...m, id: savedId } : m);
            return { ...prev, [selectedConversationId]: updated };
          });
        }
      } else {
        antdMessage.error('Failed to send message');
      }
    } catch {
      antdMessage.error('Network error sending message');
    }
  };

  return (
    <div style={{ padding: isMobile ? 8 : 12, overflow: 'hidden', height: '100%' }}>
      <div style={{ width: '100%', margin: '0', height: '100%', overflow: 'hidden' }}>
        <Card
          title={
            <Space>
              <Title level={4} style={{ margin: 0 }}>Messages</Title>
              {isConnected ? <Badge status="success" text="Connected" /> : <Badge status="error" text="Disconnected" />}
            </Space>
          }
          style={{ borderRadius: 12, height: isMobile ? 'calc(100vh - 190px)' : 'calc(100vh - 220px)' }}
          styles={{ body: { height: '100%', padding: 12, overflow: 'hidden' } }}
        >
          <div style={{ display: 'flex', height: '100%', gap: 12, minHeight: 0, overflow: 'hidden' }}>
            {/* ── Conversation list ── */}
            <div
              style={{
                width: isMobile ? '100%' : '32%',
                display: isMobile && selectedConversationId ? 'none' : 'flex',
                flexDirection: 'column',
                minWidth: 0,
                minHeight: 0,
                overflow: 'hidden',
                background: isDarkMode ? '#1a1a1a' : '#f7f8fa',
                borderRadius: 10,
                border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #eceff3',
                padding: 10,
              }}
            >
              <Text strong style={{ marginBottom: 10, fontSize: 16 }}>Chats</Text>
              <Input
                placeholder="Search people or messages"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{ marginBottom: 12, borderRadius: 10 }}
              />

              {loadingContacts ? (
                <Text type="secondary">Loading conversations...</Text>
              ) : filteredConversations.length === 0 ? (
                <Empty description={<Text type="secondary">No conversation found.</Text>} />
              ) : (
                <List
                  dataSource={filteredConversations}
                  style={{ paddingRight: 4, flex: 1, minHeight: 0 }}
                  renderItem={(conv) => (
                    <List.Item
                      onClick={() => openConversation(conv.userId)}
                      style={{
                        cursor: 'pointer',
                        border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #dde2ea',
                        borderRadius: 12,
                        marginBottom: 8,
                        padding: '12px',
                        background: selectedConversationId === conv.userId
                          ? (isDarkMode ? 'rgba(124, 58, 237, 0.2)' : '#e9f2ff')
                          : (isDarkMode ? '#1e1e1e' : '#ffffff'),
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 12,
                        transition: 'all 0.2s ease',
                      }}
                    >
                      <Avatar size={46} style={{ background: isDarkMode ? '#3a3a3a' : '#e7e7e7', color: isDarkMode ? '#f3f4f6' : '#000', fontWeight: 700, flexShrink: 0 }}>
                        {getAvatarLabel(conv.userName)}
                      </Avatar>
                      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                          <Text strong style={{ fontSize: 15, display: 'block', lineHeight: 1.2 }}>{conv.userName}</Text>
                          <Text type="secondary" style={{ fontSize: 11, flexShrink: 0 }}>{formatTime(conv.updatedAt)}</Text>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                          <Text style={{ color: '#5a6372', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0 }}>
                            {conv.preview}
                          </Text>
                          {conv.unreadCount > 0 && (
                            <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', minWidth: '24px', height: '24px', padding: '0 8px', borderRadius: '12px', backgroundColor: '#ff4d4f', color: '#fff', fontSize: '12px', fontWeight: 'bold', flexShrink: 0 }}>
                              {conv.unreadCount > 9 ? '9+' : conv.unreadCount}
                            </span>
                          )}
                        </div>
                      </div>
                    </List.Item>
                  )}
                />
              )}
            </div>

            {/* ── Chat window ── */}
            <div
              style={{
                width: isMobile ? '100%' : '68%',
                display: isMobile && !selectedConversationId ? 'none' : 'flex',
                flexDirection: 'column',
                minWidth: 0,
                minHeight: 0,
                overflow: 'hidden',
                border: 'none',
                borderRadius: 12,
                background: isDarkMode ? '#1a1a1a' : '#fff',
                boxShadow: isDarkMode ? '0 2px 8px rgba(0,0,0,0.4)' : '0 2px 8px rgba(0,0,0,0.1)',
              }}
            >
              {!selectedConversation ? (
                <div style={{ margin: 'auto' }}>
                  <Empty description="Select a conversation to start chatting" />
                </div>
              ) : (
                <>
                  {/* Header */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', background: isDarkMode ? '#1e1e1e' : '#2C3E50', color: '#fff' }}>
                    {isMobile && (
                      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => setSelectedConversationId(null)} style={{ color: '#fff' }} />
                    )}
                    <div style={{ position: 'relative' }}>
                      <Avatar size={44} style={{ background: isDarkMode ? '#7c3aed' : '#4e73ff', color: '#fff', fontWeight: 700, fontSize: 18 }}>
                        {getAvatarLabel(selectedConversation.userName)}
                      </Avatar>
                      {isConnected && (
                        <div style={{ position: 'absolute', bottom: 0, right: 0, width: 14, height: 14, borderRadius: '50%', background: '#52c41a', border: isDarkMode ? '2px solid #1e1e1e' : '2px solid #2C3E50' }} />
                      )}
                    </div>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <Text strong style={{ display: 'block', color: '#fff', fontSize: 16 }}>{selectedConversation.userName}</Text>
                      <Space size={8} style={{ fontSize: 12 }}>
                        <Text style={{ color: 'rgba(255,255,255,0.85)' }}>{isConnected ? 'Active now' : 'Offline'}</Text>
                        {selectedConversation.userName.includes('Tenant') && (
                          <Badge count="TENANT" style={{ backgroundColor: 'rgba(255,255,255,0.2)', color: '#fff', fontSize: 10, fontWeight: 600, height: 18, lineHeight: '18px' }} />
                        )}
                      </Space>
                    </div>
                    <Space>
                      {selectedConversation.unreadCount > 0 && (
                        <Button type="primary" icon={<CheckOutlined />} onClick={markAsRead} style={{ backgroundColor: '#52c41a', borderColor: '#52c41a', fontWeight: 600 }}>
                          Mark as Read
                        </Button>
                      )}
                      <Button danger icon={<DeleteOutlined />} onClick={handleDeleteConversation} loading={deletingConversationId === selectedConversation.userId} style={{ fontWeight: 600 }}>
                        Delete
                      </Button>
                    </Space>
                  </div>

                  {/* Messages */}
                  <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', padding: '20px', display: 'flex', flexDirection: 'column', gap: 16, background: isDarkMode ? '#0f0f0f' : '#E9ECEF' }}>
                    {loadingHistoryByUser[selectedConversation.userId] ? (
                      <Text type="secondary" style={{ textAlign: 'center', marginTop: 20 }}>Loading messages...</Text>
                    ) : selectedMessages.length === 0 ? (
                      <Text type="secondary" style={{ textAlign: 'center', marginTop: 20 }}>No messages yet. Say hello 👋</Text>
                    ) : (
                      selectedMessages.map((item, index) => {
                        const showDate = index === 0 ||
                          new Date(selectedMessages[index - 1].timestamp).toDateString() !== new Date(item.timestamp).toDateString();
                        return (
                          <React.Fragment key={item.id}>
                            {showDate && (
                              <div style={{ textAlign: 'center', margin: '8px 0' }}>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                  {new Date(item.timestamp).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })} · {formatTime(item.timestamp)}
                                </Text>
                              </div>
                            )}
                            <div style={{ display: 'flex', justifyContent: item.isMine ? 'flex-end' : 'flex-start', gap: 8, alignItems: 'flex-end' }}>
                              {!item.isMine && (
                                <Avatar size={32} style={{ background: isDarkMode ? '#3a3a3a' : '#4e73ff', color: isDarkMode ? '#f3f4f6' : '#fff', fontWeight: 700, flexShrink: 0 }}>
                                  {getAvatarLabel(selectedConversation.userName)}
                                </Avatar>
                              )}
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: item.isMine ? 'flex-end' : 'flex-start', maxWidth: '65%' }}>
                                <div style={{ padding: '10px 14px', borderRadius: item.isMine ? '18px 18px 4px 18px' : '18px 18px 18px 4px', background: item.isMine ? (isDarkMode ? '#7c3aed' : '#1890ff') : (isDarkMode ? '#2a2a2a' : '#ffffff'), boxShadow: '0 1px 2px rgba(0,0,0,0.1)', border: !item.isMine && !isDarkMode ? '1px solid #f0f0f0' : 'none' }}>
                                  <Text style={{ color: item.isMine ? '#fff' : (isDarkMode ? '#e5e7eb' : '#000'), fontSize: 14, lineHeight: 1.5 }}>{item.text}</Text>
                                </div>
                                <Text style={{ fontSize: 11, color: '#999', marginTop: 4, marginLeft: item.isMine ? 0 : 8, marginRight: item.isMine ? 8 : 0 }}>
                                  {formatTime(item.timestamp)}
                                </Text>
                              </div>
                            </div>
                          </React.Fragment>
                        );
                      })
                    )}
                    <div ref={messagesEndRef} />
                  </div>

                  {/* Input */}
                  <div style={{ padding: '12px 16px', background: isDarkMode ? '#1a1a1a' : '#fff', borderTop: isDarkMode ? '1px solid #2a2a2a' : '1px solid #e0e0e0', display: 'flex', gap: 12, alignItems: 'center' }}>
                    <Input
                      value={draftMessage}
                      onChange={(e) => setDraftMessage(e.target.value)}
                      onPressEnter={handleSendMessage}
                      placeholder="Type a message..."
                      style={{ flex: 1, borderRadius: 20, padding: '8px 16px', background: isDarkMode ? '#1e1e1e' : '#F8F9FA', border: isDarkMode ? '1px solid #3a3a3a' : 'none' }}
                    />
                    <Button
                      type="primary"
                      shape="circle"
                      icon={<SendOutlined />}
                      size="large"
                      onClick={handleSendMessage}
                      disabled={!draftMessage.trim()}
                      style={{ width: 42, height: 42 }}
                    />
                  </div>
                </>
              )}
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default Messages;