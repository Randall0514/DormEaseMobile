import React, { useEffect, useState } from 'react';
import { Card, List, Tag, Typography, Row, Col, Space, Avatar, Button, Divider, Spin, Modal, Descriptions, message, Grid } from 'antd';
import { CalendarOutlined, UserOutlined, ClockCircleOutlined } from '@ant-design/icons';

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
  created_at: string;
  advance_used?: boolean;
  deposit_used?: boolean;
}

const statusColor = (status: string) => {
  if (status === 'approved') return 'green';
  if (status === 'rejected') return 'red';
  if (status === 'archived') return 'default';
  return 'orange';
};

type Props = { onNavigate?: (section: string) => void };

const ArchivedNotifications: React.FC<Props> = ({ onNavigate }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const isDarkMode = document.body.classList.contains('dark-mode');

  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedReservation, setSelectedReservation] = useState<Reservation | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const token = localStorage.getItem(AUTH_TOKEN_KEY);

  const fetchReservations = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/reservations`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`Error: ${res.status}`);
      const data: Reservation[] = await res.json();
      // filter archived
      setReservations(data.filter((r) => r.status === 'archived'));
    } catch (err) {
      message.error('Could not load archived reservations. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReservations();
  }, []);

  const openModal = (reservation: Reservation) => {
    setSelectedReservation(reservation);
    setModalOpen(true);
  };

  const deleteReservation = async (id: number) => {
    Modal.confirm({
      title: 'Delete archived reservation',
      content: 'Are you sure you want to permanently delete this archived reservation? This action cannot be undone.',
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        setUpdatingId(id);
        try {
          const res = await fetch(`${API_BASE}/reservations/${id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${token}` },
          });
          if (!res.ok) throw new Error('Failed to delete');
          setReservations((prev) => prev.filter((r) => r.id !== id));
          message.success('Archived reservation deleted');
        } catch (err) {
          message.error('Failed to delete archived reservation.');
        } finally {
          setUpdatingId(null);
        }
      },
    });
  };

  const unarchiveReservation = async (id: number) => {
    setUpdatingId(id);
    try {
      const res = await fetch(`${API_BASE}/reservations/${id}/unarchive`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to unarchive');
      setReservations((prev) => prev.filter((r) => r.id !== id));
      message.success('Reservation restored to Booking Notifications');
    } catch (err) {
      message.error('Failed to unarchive reservation.');
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24, overflowX: 'hidden' }}>
      <div style={{ maxWidth: 980, width: '100%', margin: '0 auto' }}>
        <Card title={<Title level={4} style={{ margin: 0 }}>Archived Notifications</Title>} extra={<Button onClick={() => onNavigate?.('notifications')}>Back</Button>} style={{ borderRadius: 12 }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Spin size="large" />
            </div>
          ) : reservations.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Text type="secondary">No archived reservations.</Text>
            </div>
          ) : (
            <List
              dataSource={reservations}
              split={false}
              renderItem={(item) => (
                <List.Item>
                  <Card hoverable style={{ width: '100%', borderRadius: 12, border: '1px solid #e9f1ff', background: '#f8fafb' }}>
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Row justify="space-between" align="middle">
                        <Col>
                          <Space align="center">
                            <CalendarOutlined style={{ fontSize: 18, color: '#1890ff' }} />
                            <div>
                              <Text strong style={{ display: 'block' }}>Archived Booking</Text>
                              <Text type="secondary">
                                <ClockCircleOutlined style={{ marginRight: 4 }} />
                                {new Date(item.created_at).toLocaleString()}
                              </Text>
                            </div>
                          </Space>
                        </Col>
                        <Col>
                          <Tag color={statusColor(item.status)} style={{ textTransform: 'capitalize', fontSize: 13, padding: '2px 10px' }}>{item.status}</Tag>
                        </Col>
                      </Row>

                      <Card style={{ borderRadius: 8, background: '#ffffff', border: '1px solid #eef6ff' }}>
                        <Row gutter={[16, 12]}>
                          <Col xs={24} sm={16}>
                            <Row gutter={[8, 8]}>
                              <Col xs={24} sm={8}>
                                <Text type="secondary" style={{ fontSize: 12 }}>STUDENT NAME</Text>
                                <br />
                                <Text strong>{item.full_name}</Text>
                              </Col>
                              <Col xs={24} sm={8}>
                                <Text type="secondary" style={{ fontSize: 12 }}>DORM NAME</Text>
                                <br />
                                <Text strong>{item.dorm_name}</Text>
                              </Col>
                              <Col xs={24} sm={8}>
                                <Text type="secondary" style={{ fontSize: 12 }}>MOVE-IN DATE</Text>
                                <br />
                                <Text strong>{item.move_in_date}</Text>
                              </Col>
                            </Row>
                          </Col>

                          <Col xs={24} sm={8} style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                            <div>
                              <Text type="secondary" style={{ fontSize: 12 }}>PAYMENT METHOD</Text>
                              <br />
                              <Tag color="blue">{item.payment_method === 'cash_on_move_in' ? 'Cash on Move-In' : item.payment_method}</Tag>
                            </div>
                            <div style={{ marginTop: 8 }}>
                              <Text type="secondary" style={{ fontSize: 12 }}>TOTAL AMOUNT</Text>
                              <br />
                              <Text strong style={{ color: '#2979FF' }}>₱{Number(item.total_amount).toLocaleString()}</Text>
                            </div>
                          </Col>
                        </Row>

                        <Divider style={{ margin: '12px 0' }} />

                        <Row justify="space-between" align="middle" gutter={[12, 12]}>
                          <Col>
                            <Space align="center">
                              <Avatar size={48} icon={<UserOutlined />} />
                              <div>
                                <Text strong>{item.full_name}</Text>
                                <br />
                                <Text type="secondary">{item.phone} · {item.duration_months} month(s)</Text>
                              </div>
                            </Space>
                          </Col>

                          <Col>
                            <Space wrap>
                              <Button onClick={() => openModal(item)}>View Details</Button>
                              <Button onClick={() => unarchiveReservation(item.id)} loading={updatingId === item.id}>Unarchive</Button>
                              <Button danger onClick={() => deleteReservation(item.id)} loading={updatingId === item.id}>Delete</Button>
                            </Space>
                          </Col>
                        </Row>
                      </Card>
                    </Space>
                  </Card>
                </List.Item>
              )}
            />
          )}
        </Card>
      </div>

      <Modal title="Reservation Details" open={modalOpen} onCancel={() => setModalOpen(false)} footer={[<Button key="close" onClick={() => setModalOpen(false)}>Close</Button>]} width={isMobile ? '95vw' : 600}>
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
            <Descriptions.Item label="Total Amount"><Text strong style={{ color: '#2979FF' }}>₱{Number(selectedReservation.total_amount).toLocaleString()}</Text></Descriptions.Item>
            <Descriptions.Item label="Payment Method" span={2}>{selectedReservation.payment_method === 'cash_on_move_in' ? 'Cash on Move-In' : selectedReservation.payment_method}</Descriptions.Item>
            <Descriptions.Item label="Notes" span={2}>{selectedReservation.notes || '—'}</Descriptions.Item>
            <Descriptions.Item label="Status" span={2}><Tag color={statusColor(selectedReservation.status)} style={{ textTransform: 'capitalize' }}>{selectedReservation.status}</Tag></Descriptions.Item>
            <Descriptions.Item label="Submitted At" span={2}>{new Date(selectedReservation.created_at).toLocaleString()}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default ArchivedNotifications;
