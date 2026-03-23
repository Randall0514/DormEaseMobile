import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Grid,
  Card, List, Tag, Typography, Row, Col, Space,
  Avatar, Button, Divider, Spin, Modal, Descriptions, message, Badge, Tooltip, Input, Alert
} from 'antd';
import {
  CalendarOutlined, UserOutlined, ClockCircleOutlined,
  LeftOutlined, CheckCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons';
import { useWebSocket } from './contexts/WebSocketContext';

const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

const API_BASE = 'http://localhost:3000';
const AUTH_TOKEN_KEY = 'dormease_token';

interface Reservation {
  id: number;
  dorm_name: string;
  location: string;
  full_name: string;
  phone: string;
  move_in_date: string;
  duration_months: number;
  price_per_month: number;
  deposit: number;
  advance: number;
  total_amount: number;
  notes: string;
  payment_method: string;
  status: string;
  rejection_reason?: string;
  termination_reason?: string | null;
  appeal_message?: string | null;
  appeal_submitted_at?: string | null;
  tenant_action?: string | null;
  cancel_reason?: string | null;
  tenant_action_at?: string | null;
  created_at: string;
  room_capacity?: number;
  dorm_id?: number;
  advance_used?: boolean;
  deposit_used?: boolean;
}

const statusColor = (status: string) => {
  if (status === 'approved') return 'green';
  if (status === 'rejected') return 'red';
  if (status === 'archived') return 'gold';
  return 'orange';
};

const tenantActionColor = (action?: string | null) => {
  if (action === 'accepted') return 'green';
  if (action === 'cancelled') return 'red';
  return 'default';
};

const tenantActionLabel = (action?: string | null) => {
  if (action === 'accepted') return '✓ Tenant Accepted';
  if (action === 'cancelled') return '✗ Tenant Cancelled';
  return null;
};

const isTenantTerminated = (reservation: Reservation) =>
  reservation.status === 'archived' && Boolean(reservation.termination_reason);

type Props = {
  onNavigate?: (section: string) => void;
};

const Notifications: React.FC<Props> = ({ onNavigate }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const { onNotification, offNotification } = useWebSocket();

  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedReservation, setSelectedReservation] = useState<Reservation | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [newTenantActionIds, setNewTenantActionIds] = useState<Set<number>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [dismissingAppealId, setDismissingAppealId] = useState<number | null>(null);

  const token = localStorage.getItem(AUTH_TOKEN_KEY);

  const prevReservationsRef = useRef<Reservation[]>([]);
  const hasLoadedOnce = useRef(false);

  // ── Core fetch ─────────────────────────────────────────────────────────────
  // Wrapped in useCallback so it's stable and safe to use in the Socket.IO
  // useEffect dependency array without causing infinite re-renders.
  const fetchReservations = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/reservations`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`Error: ${res.status}`);
      const data: Reservation[] = await res.json();
      const filtered = data.filter(
        (r) => r.status !== 'archived' || Boolean(r.appeal_message && r.appeal_submitted_at)
      );

      if (hasLoadedOnce.current) {
        const tenantActionsArrived: number[] = [];
        const appealsArrived: number[] = [];

        filtered.forEach(incoming => {
          const previous = prevReservationsRef.current.find(p => p.id === incoming.id);

          if (incoming.tenant_action && !previous?.tenant_action) {
            tenantActionsArrived.push(incoming.id);
          }

          if (incoming.appeal_submitted_at && !previous?.appeal_submitted_at) {
            appealsArrived.push(incoming.id);
          }
        });

        if (tenantActionsArrived.length > 0) {
          setNewTenantActionIds(prev => new Set([...prev, ...tenantActionsArrived]));
          message.info({
            content: `🔔 ${tenantActionsArrived.length} tenant(s) responded to their reservation!`,
            duration: 6,
          });
        }

        if (appealsArrived.length > 0) {
          message.warning({
            content: `📋 ${appealsArrived.length} terminated tenant appeal(s) received.`,
            duration: 6,
          });
        }
      }

      prevReservationsRef.current = filtered;
      setReservations(filtered);
      hasLoadedOnce.current = true;
    } catch (err) {
      if (!silent) message.error('Could not load reservations. Please try again.');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [token]); // only re-creates if the token changes (e.g. re-login)

  // ── Initial load ───────────────────────────────────────────────────────────
  useEffect(() => {
    fetchReservations(false);
  }, [fetchReservations]);

  // ── Socket.IO real-time updates ────────────────────────────────────────────
  // Fires fetchReservations(true) the instant the server pushes
  // 'reservation_updated' or 'notification', so the list refreshes
  // immediately without any polling delay.
  useEffect(() => {
    const handleReservationUpdate = (data: any) => {
      console.log('🔔 Socket.IO push received:', data);
      fetchReservations(true); // silent refresh — no loading spinner

      // Show the server's message as an Ant Design notification banner
      if (data.message) {
        message.info({
          content: `🔔 ${data.message}`,
          duration: 5,
        });
      }
    };

    onNotification(handleReservationUpdate);

    // Cleanup: unregister when component unmounts or token changes
    return () => {
      offNotification(handleReservationUpdate);
    };
  }, [fetchReservations, onNotification, offNotification]);
  // ── End Socket.IO ──────────────────────────────────────────────────────────

  // ── Approve / Reject ───────────────────────────────────────────────────────
  const updateStatus = async (id: number, status: 'approved' | 'rejected') => {
    setUpdatingId(id);
    try {
      let body: any = { status };

      if (status === 'rejected') {
        const reason = await new Promise<string | null>(resolve => {
          let val = '';
          Modal.confirm({
            title: 'Reject reservation',
            width: isMobile ? 360 : 800,
            content: (
              <div>
                <p>Please provide a reason for rejecting this reservation:</p>
                <textarea
                  onChange={(e: any) => { val = e.target.value; }}
                  rows={6}
                  style={{ width: '100%', padding: 8, height: 160 }}
                  placeholder="Reason"
                />
              </div>
            ),
            okText: 'Next',
            cancelText: 'Cancel',
            onOk: () => { resolve(val); },
            onCancel: () => { resolve(null); },
          });
        });
        if (reason === null) { setUpdatingId(null); return; }
        const trimmed = String(reason).trim();
        if (!trimmed) { message.error('Rejection reason is required'); setUpdatingId(null); return; }

        const confirmed = await new Promise<boolean>(resolve => {
          Modal.confirm({
            title: 'Are you sure?',
            width: isMobile ? 360 : 800,
            content: (
              <div>
                <p>You're about to reject this tenant with the following reason:</p>
                <div style={{ background: '#f6f8fb', padding: 12, borderRadius: 6, maxHeight: 240, overflowY: 'auto' }}>
                  {trimmed}
                </div>
              </div>
            ),
            okText: 'Confirm Reject',
            cancelText: 'Cancel',
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
        if (!confirmed) { setUpdatingId(null); return; }
        body.rejection_reason = trimmed;

      } else if (status === 'approved') {
        const reservation = reservations.find(r => r.id === id);
        const currentOccupied = getOccupiedBeds(reservation?.dorm_id);
        const capacity = reservation?.room_capacity || 0;
        const spotsLeft = capacity - currentOccupied;
        const isDormAtCapacity = spotsLeft <= 1;

        const confirmed = await new Promise<boolean>(resolve => {
          Modal.confirm({
            title: 'Confirm Booking',
            width: isMobile ? 360 : 600,
            content: (
              <div>
                {isDormAtCapacity && (
                  <Alert
                    message="⚠️ Dorm is At/Near Capacity"
                    description={
                      spotsLeft === 1
                        ? `After confirming this tenant, your dorm will have only ${spotsLeft} spot left. Once this tenant accepts, your dorm will be full.`
                        : `Your dorm is already at capacity (${currentOccupied}/${capacity} beds occupied). Cannot add more tenants.`
                    }
                    type="warning"
                    showIcon
                    style={{ marginBottom: 16, borderRadius: 8 }}
                  />
                )}
                <p>Are you sure you want to confirm this reservation?</p>
                <div style={{ background: '#f3fbff', padding: 12, borderRadius: 6, marginTop: 12 }}>
                  <div style={{ marginBottom: 8 }}>
                    <Text strong>{reservation?.full_name}</Text><br />
                    <Text type="secondary">{reservation?.dorm_name} · {reservation?.move_in_date}</Text>
                  </div>
                  <Text type="secondary">
                    ₱{Number(reservation?.total_amount).toLocaleString()} for {reservation?.duration_months} month(s)
                  </Text>
                  <br />
                  <Text type="secondary" style={{ marginTop: 8, display: 'block', fontSize: 12 }}>
                    Beds: {currentOccupied}/{capacity} occupied
                  </Text>
                </div>
              </div>
            ),
            okText: spotsLeft <= 0 ? 'Cannot Confirm (Full)' : 'Confirm Booking',
            cancelText: 'Cancel',
            okType: spotsLeft <= 0 ? 'default' : 'primary',
            okButtonProps: { danger: spotsLeft <= 0, disabled: spotsLeft <= 0 },
            onOk: () => spotsLeft > 0 ? resolve(true) : resolve(false),
            onCancel: () => resolve(false),
          });
        });
        if (!confirmed) { setUpdatingId(null); return; }
      }

      const res = await fetch(`${API_BASE}/reservations/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => ({}));
        throw new Error(errBody?.message || 'Failed to update');
      }
      setReservations(prev => prev.map(r => r.id === id ? { ...r, status } : r));
      prevReservationsRef.current = prevReservationsRef.current.map(r => r.id === id ? { ...r, status } : r);
      message.success(status === 'approved' ? 'Booking confirmed!' : 'Booking rejected.');
    } catch (err: any) {
      message.error(err?.message || 'Failed to update reservation status.');
    } finally {
      setUpdatingId(null);
    }
  };

  // ── Dismiss Appeal ─────────────────────────────────────────────────────────
  const dismissAppeal = async (id: number) => {
    const confirmed = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: 'Dismiss Tenant Appeal?',
        width: isMobile ? 360 : 560,
        content: (
          <div>
            <p>This will remove the tenant appeal from your notifications.</p>
            <Text type="secondary">The tenant will not be able to submit another appeal for this reservation.</Text>
          </div>
        ),
        okText: 'Yes, Dismiss Appeal',
        cancelText: 'Cancel',
        okButtonProps: { danger: true },
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

    if (!confirmed) {
      return;
    }

    setDismissingAppealId(id);
    try {
      const res = await fetch(`${API_BASE}/reservations/${id}/dismiss-appeal`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to dismiss appeal');
      setReservations(prev => prev.filter(r => r.id !== id));
      prevReservationsRef.current = prevReservationsRef.current.filter(r => r.id !== id);
      message.success('Appeal moved to archive');
      onNavigate?.('archived');
    } catch {
      message.error('Failed to dismiss appeal.');
    } finally {
      setDismissingAppealId(null);
    }
  };

  // ── Archive ────────────────────────────────────────────────────────────────
  const archiveReservation = async (id: number) => {
    setUpdatingId(id);
    try {
      const res = await fetch(`${API_BASE}/reservations/${id}/archive`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({}),
      });
      if (!res.ok) throw new Error('Failed to archive');
      setReservations(prev => prev.map(r => r.id === id ? { ...r, status: 'archived' } : r));
      prevReservationsRef.current = prevReservationsRef.current.filter(r => r.id !== id);
      message.success('Reservation archived');
    } catch {
      message.error('Failed to archive reservation.');
    } finally {
      setUpdatingId(null);
    }
  };

  const openModal = (reservation: Reservation) => {
    setNewTenantActionIds(prev => {
      const next = new Set(prev);
      next.delete(reservation.id);
      return next;
    });
    setSelectedReservation(reservation);
    setModalOpen(true);
  };

  const getFilteredReservations = () => {
    if (!searchQuery.trim()) return reservations;
    const query = searchQuery.toLowerCase();
    return reservations.filter(r =>
      r.full_name.toLowerCase().includes(query) ||
      r.dorm_name.toLowerCase().includes(query) ||
      r.location.toLowerCase().includes(query) ||
      r.phone.toLowerCase().includes(query)
    );
  };

  const getOccupiedBeds = (dorm_id?: number) => {
    if (!dorm_id) return 0;
    return reservations.filter(r =>
      r.dorm_id === dorm_id &&
      r.status === 'approved' &&
      r.tenant_action === 'accepted'
    ).length;
  };

  const isDormFull = () => {
    if (reservations.length === 0) return false;
    const firstReservation = reservations[0];
    if (!firstReservation.room_capacity || !firstReservation.dorm_id) return false;
    const occupied = getOccupiedBeds(firstReservation.dorm_id);
    return occupied >= firstReservation.room_capacity;
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div style={{ padding: isMobile ? 12 : 24, overflowX: 'hidden' }}>
      <div style={{ maxWidth: 1300, width: '100%', margin: '0 auto 12px', display: 'flex', justifyContent: 'flex-start' }}>
        <Button icon={<LeftOutlined />} onClick={() => onNavigate?.('home')}>Back</Button>
      </div>

      <div style={{ maxWidth: 1300, width: '100%', margin: '0 auto' }}>
        <Card
          title={<Title level={4} style={{ margin: 0 }}>Booking and Appeal Notifications</Title>}
          extra={<Button onClick={() => onNavigate?.('archived')}>Archived</Button>}
          style={{ borderRadius: 12 }}
        >
          {isDormFull() && (
            <Alert
              message="🎉 Your Dorm is Full!"
              description={(() => {
                const firstRes = reservations[0];
                return `All ${firstRes.room_capacity} beds in ${firstRes.dorm_name} are now occupied. Congratulations!`;
              })()}
              type="success"
              showIcon
              closable
              style={{ marginBottom: 20, borderRadius: 12 }}
            />
          )}
          <div style={{ marginBottom: 20 }}>
            <Input.Search
              placeholder="Search dorms, owners, names, or phone..."
              allowClear
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{ width: '100%', maxWidth: isMobile ? '100%' : 360 }}
            />
          </div>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
          ) : reservations.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Text type="secondary">No notifications yet.</Text>
            </div>
          ) : getFilteredReservations().length === 0 ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Text type="secondary">No reservations match your search.</Text>
            </div>
          ) : (
            <List
              dataSource={getFilteredReservations()}
              split={false}
              renderItem={item => {
                const isAppeal = Boolean(item.appeal_message && item.appeal_submitted_at);
                const terminated = isTenantTerminated(item);
                const isNew = newTenantActionIds.has(item.id);
                const actionLabel = terminated ? null : tenantActionLabel(item.tenant_action);

                return (
                  <List.Item>
                    <Badge.Ribbon
                      text={isAppeal ? '📋 Tenant Appeal' : isNew ? '🔔 Tenant Responded' : ''}
                      color={isAppeal ? '#faad14' : isNew ? '#f5222d' : 'transparent'}
                      style={{ display: isAppeal || isNew ? undefined : 'none' }}
                    >
                      <Card
                        hoverable
                        style={{
                          width: '100%',
                          borderRadius: 12,
                          border: isAppeal
                            ? '2px solid #faad14'
                            : isNew
                              ? '2px solid #f5222d'
                              : '1px solid #e9f1ff',
                          background: isAppeal
                            ? '#fffbe6'
                            : isNew
                              ? '#fff1f0'
                              : '#f3fbff',
                          transition: 'border 0.3s, background 0.3s',
                        }}
                        bodyStyle={{ padding: '20px 24px' }}
                      >
                        <Space direction="vertical" style={{ width: '100%' }}>

                          {/* Header */}
                          <Row justify="space-between" align={isMobile ? 'top' : 'middle'} gutter={[8, 8]}>
                            <Col>
                              <Space align="center">
                                <CalendarOutlined style={{ fontSize: 18, color: '#1890ff' }} />
                                <div>
                                  <Text strong style={{ display: 'block' }}>
                                    {isAppeal ? 'Tenant Appeal' : 'New Booking'}
                                  </Text>
                                  <Text type="secondary">
                                    <ClockCircleOutlined style={{ marginRight: 4 }} />
                                    {new Date(isAppeal ? item.appeal_submitted_at || item.created_at : item.created_at).toLocaleString()}
                                  </Text>
                                </div>
                              </Space>
                            </Col>
                            <Col>
                              <Space style={{ flexWrap: 'wrap' }}>
                                <Tag
                                  color={statusColor(item.status)}
                                  style={{ textTransform: 'capitalize', fontSize: 13, padding: '2px 10px' }}
                                >
                                  {item.status}
                                </Tag>

                                {isAppeal && (
                                  <Tag color="gold" style={{ fontSize: 13, padding: '2px 10px' }}>
                                    Appeal Submitted
                                  </Tag>
                                )}

                                {terminated && (
                                  <Tag color="red" style={{ fontSize: 13, padding: '2px 10px' }}>
                                    Tenant Terminated
                                  </Tag>
                                )}

                                {actionLabel && (
                                  <Tooltip
                                    title={
                                      item.tenant_action === 'cancelled'
                                        ? `Reason: ${item.cancel_reason || '—'}`
                                        : `Accepted at ${item.tenant_action_at
                                            ? new Date(item.tenant_action_at).toLocaleString()
                                            : '—'}`
                                    }
                                  >
                                    <Tag
                                      color={tenantActionColor(item.tenant_action)}
                                      icon={item.tenant_action === 'accepted'
                                        ? <CheckCircleOutlined />
                                        : <CloseCircleOutlined />}
                                      style={{ fontSize: 13, padding: '2px 10px' }}
                                    >
                                      {actionLabel}
                                    </Tag>
                                  </Tooltip>
                                )}
                              </Space>
                            </Col>
                          </Row>

                          {/* Cancellation reason banner */}
                          {item.tenant_action === 'cancelled' && item.cancel_reason && (
                            <div style={{
                              background: '#fff1f0',
                              border: '1px solid #ffa39e',
                              borderRadius: 6,
                              padding: '8px 12px',
                            }}>
                              <Text type="danger" strong>
                                <CloseCircleOutlined style={{ marginRight: 6 }} />
                                Tenant cancellation reason:
                              </Text>
                              <br />
                              <Text>{item.cancel_reason}</Text>
                            </div>
                          )}

                          {isAppeal && (
                            <div style={{
                              background: '#fff7e6',
                              border: '1px solid #ffd591',
                              borderRadius: 6,
                              padding: '10px 12px',
                            }}>
                              <Text strong style={{ color: '#ad6800' }}>Appeal message:</Text>
                              <br />
                              <Text>{item.appeal_message}</Text>
                              {item.termination_reason && (
                                <>
                                  <br />
                                  <br />
                                  <Text strong style={{ color: '#ad6800' }}>Termination reason:</Text>
                                  <br />
                                  <Text>{item.termination_reason}</Text>
                                </>
                              )}
                            </div>
                          )}

                          {/* Detail card */}
                          <Card style={{ borderRadius: 8, background: '#ffffff', border: '1px solid #eef6ff' }} bodyStyle={{ padding: '20px 24px' }}>
                            <Row gutter={[20, 16]} align="top">
                              <Col xs={24} sm={4}>
                                <Text type="secondary" style={{ fontSize: 11, fontWeight: 600 }}>STUDENT NAME</Text><br />
                                <Text strong style={{ fontSize: 14, marginTop: 4, display: 'block' }}>{item.full_name}</Text>
                              </Col>
                              <Col xs={24} sm={4}>
                                <Text type="secondary" style={{ fontSize: 11, fontWeight: 600 }}>DORM NAME</Text><br />
                                <Text strong style={{ fontSize: 14, marginTop: 4, display: 'block' }}>{item.dorm_name}</Text>
                                {item.room_capacity && (() => {
                                  const occupied = getOccupiedBeds(item.dorm_id);
                                  const unoccupied = (item.room_capacity || 0) - occupied;
                                  const isFull = unoccupied === 0;
                                  return (
                                    <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                      <div style={{
                                        background: isFull ? '#d4380d' : '#2D4A7C',
                                        color: 'white',
                                        padding: '4px 12px',
                                        borderRadius: 20,
                                        fontSize: 12,
                                        fontWeight: 600,
                                      }}>
                                        Occupied Beds: {occupied}
                                      </div>
                                      <div style={{
                                        background: isFull ? '#d4380d' : '#2D4A7C',
                                        color: 'white',
                                        padding: '4px 12px',
                                        borderRadius: 20,
                                        fontSize: 12,
                                        fontWeight: 600,
                                      }}>
                                        Unoccupied Beds: {unoccupied}
                                      </div>
                                      {isFull && (
                                        <div style={{
                                          background: '#faad14',
                                          color: 'white',
                                          padding: '4px 12px',
                                          borderRadius: 20,
                                          fontSize: 12,
                                          fontWeight: 600,
                                        }}>
                                          🔴 FULL
                                        </div>
                                      )}
                                    </div>
                                  );
                                })()}
                              </Col>
                              <Col xs={24} sm={4}>
                                <Text type="secondary" style={{ fontSize: 11, fontWeight: 600 }}>MOVE-IN DATE</Text><br />
                                <Text strong style={{ fontSize: 14, marginTop: 4, display: 'block' }}>{item.move_in_date}</Text>
                              </Col>
                              <Col xs={24} sm={5}>
                                <Text type="secondary" style={{ fontSize: 11, fontWeight: 600 }}>PAYMENT METHOD</Text><br />
                                <Tag color="blue" style={{ marginTop: 4 }}>
                                  {item.payment_method === 'cash_on_move_in' ? 'Cash on Move-In' : item.payment_method}
                                </Tag>
                              </Col>
                              <Col xs={24} sm={7}>
                                <Text type="secondary" style={{ fontSize: 11, fontWeight: 600 }}>TOTAL AMOUNT</Text><br />
                                <Text strong style={{ color: '#2979FF', fontSize: 15, marginTop: 4, display: 'block' }}>
                                  ₱{Number(item.total_amount).toLocaleString()}
                                </Text>
                              </Col>
                            </Row>

                            <Divider style={{ margin: '16px 0' }} />

                            <Row justify="space-between" align="middle" gutter={[12, 12]}>
                              <Col>
                                <Space align="center" size={12}>
                                  <Avatar size={48} icon={<UserOutlined />} />
                                  <div>
                                    <Text strong style={{ fontSize: 14 }}>{item.full_name}</Text><br />
                                    <Text type="secondary" style={{ fontSize: 12 }}>{item.phone} · {item.duration_months} month(s)</Text>
                                  </div>
                                </Space>
                              </Col>
                              <Col>
                                <Space wrap>
                                  <Button onClick={() => openModal(item)}>View Details</Button>

                                  {item.status === 'pending' && (
                                    <>
                                      <Button
                                        danger
                                        loading={updatingId === item.id}
                                        onClick={() => updateStatus(item.id, 'rejected')}
                                      >
                                        Reject
                                      </Button>
                                      <Button
                                        type="primary"
                                        loading={updatingId === item.id}
                                        onClick={() => updateStatus(item.id, 'approved')}
                                      >
                                        Confirm Booking
                                      </Button>
                                    </>
                                  )}

                                  {isAppeal && (
                                    <Button
                                      onClick={() => dismissAppeal(item.id)}
                                      loading={dismissingAppealId === item.id}
                                    >
                                      Appeal Received
                                    </Button>
                                  )}

                                  {!isAppeal && item.status !== 'archived' && (
                                    <Button
                                      onClick={() => archiveReservation(item.id)}
                                      loading={updatingId === item.id}
                                    >
                                      Archive
                                    </Button>
                                  )}

                                  {item.status === 'approved' && !item.tenant_action && (
                                    <Tag color="green" style={{ padding: '4px 12px', fontSize: 13 }}>✓ Confirmed</Tag>
                                  )}
                                  {item.status === 'rejected' && !item.tenant_action && (
                                    <Tag color="red" style={{ padding: '4px 12px', fontSize: 13 }}>✗ Rejected</Tag>
                                  )}
                                </Space>
                              </Col>
                            </Row>
                          </Card>

                        </Space>
                      </Card>
                    </Badge.Ribbon>
                  </List.Item>
                );
              }}
            />
          )}
        </Card>
      </div>

      {/* View Details Modal */}
      <Modal
        title="Reservation Details"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={[<Button key="close" onClick={() => setModalOpen(false)}>Close</Button>]}
        width={isMobile ? '95vw' : 600}
      >
        {selectedReservation && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Full Name" span={2}>{selectedReservation.full_name}</Descriptions.Item>
            <Descriptions.Item label="Phone">{selectedReservation.phone}</Descriptions.Item>
            <Descriptions.Item label="Dorm Name">{selectedReservation.dorm_name}</Descriptions.Item>
            <Descriptions.Item label="Location" span={2}>{selectedReservation.location}</Descriptions.Item>
            <Descriptions.Item label="Move-in Date">{selectedReservation.move_in_date}</Descriptions.Item>
            <Descriptions.Item label="Duration">{selectedReservation.duration_months} month(s)</Descriptions.Item>
            <Descriptions.Item label="Price / Month">₱{Number(selectedReservation.price_per_month).toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="Deposit">₱{Number(selectedReservation.deposit).toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="Advance">₱{Number(selectedReservation.advance).toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="Total Amount">
              <Text strong style={{ color: '#2979FF' }}>₱{Number(selectedReservation.total_amount).toLocaleString()}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Payment Method" span={2}>
              {selectedReservation.payment_method === 'cash_on_move_in' ? 'Cash on Move-In' : selectedReservation.payment_method}
            </Descriptions.Item>
            <Descriptions.Item label="Notes" span={2}>{selectedReservation.notes || '—'}</Descriptions.Item>
            <Descriptions.Item label="Owner Status" span={2}>
              <Tag color={statusColor(selectedReservation.status)} style={{ textTransform: 'capitalize' }}>
                {selectedReservation.status}
              </Tag>
            </Descriptions.Item>
            {selectedReservation.termination_reason && (
              <Descriptions.Item label="Termination Reason" span={2}>
                {selectedReservation.termination_reason}
              </Descriptions.Item>
            )}
            {selectedReservation.appeal_message && (
              <Descriptions.Item label="Appeal Message" span={2}>
                {selectedReservation.appeal_message}
              </Descriptions.Item>
            )}
            {selectedReservation.appeal_submitted_at && (
              <Descriptions.Item label="Appeal Submitted At" span={2}>
                {new Date(selectedReservation.appeal_submitted_at).toLocaleString()}
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Tenant Response" span={2}>
              {isTenantTerminated(selectedReservation) ? (
                <Tag color="red" style={{ fontSize: 13 }}>
                  Tenant Terminated
                </Tag>
              ) : selectedReservation.tenant_action ? (
                <Space direction="vertical">
                  <Tag
                    color={tenantActionColor(selectedReservation.tenant_action)}
                    icon={selectedReservation.tenant_action === 'accepted'
                      ? <CheckCircleOutlined />
                      : <CloseCircleOutlined />}
                    style={{ fontSize: 13 }}
                  >
                    {tenantActionLabel(selectedReservation.tenant_action)}
                  </Tag>
                  {selectedReservation.tenant_action_at && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      at {new Date(selectedReservation.tenant_action_at).toLocaleString()}
                    </Text>
                  )}
                  {selectedReservation.tenant_action === 'cancelled' && selectedReservation.cancel_reason && (
                    <div style={{ background: '#fff1f0', padding: 8, borderRadius: 6, marginTop: 4 }}>
                      <Text type="danger">Reason: {selectedReservation.cancel_reason}</Text>
                    </div>
                  )}
                </Space>
              ) : (
                <Text type="secondary">No response yet</Text>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Submitted At" span={2}>
              {new Date(selectedReservation.created_at).toLocaleString()}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default Notifications;