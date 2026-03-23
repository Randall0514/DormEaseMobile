import React, { useEffect, useState } from 'react';
import {
  Avatar,
  Card,
  Typography,
  Space,
  Button,
  Row,
  Col,
  Descriptions,
  Modal,
  Form,
  Input,
  message,
  Divider,
  Spin,
  Badge,
  Tag,
  Grid,
  Table,
  Tabs,
  Alert,
} from 'antd';
import {
  UserOutlined,
  EditOutlined,
  MailOutlined,
  LogoutOutlined,
  CheckCircleOutlined,
  DollarOutlined,
  LockOutlined,
  InfoCircleOutlined,
  CreditCardOutlined,
  SearchOutlined,
} from '@ant-design/icons';

const { Title, Text } = Typography;
const { useBreakpoint } = Grid;
const API_BASE = 'http://localhost:3000';

const Profile: React.FC = () => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const isDarkMode = document.body.classList.contains('dark-mode');
  const [user, setUser] = useState<{ id?: number; username?: string; email?: string; phoneNumber?: string; fullName?: string; platform?: string } | null>(null);
  const [loading, setLoading] = useState(true);

  const [activeTab, setActiveTab] = useState<string>('personal');
  const [editing, setEditing] = useState(false);
  const [form] = Form.useForm();
  const [passForm] = Form.useForm();
  const [emailForm] = Form.useForm();
  
  // OTP verification states
  const [showPasswordOtpModal, setShowPasswordOtpModal] = useState(false);
  const [showEmailOtpModal, setShowEmailOtpModal] = useState(false);
  const [otpModalForm] = Form.useForm();
  const [otpSent, setOtpSent] = useState(false);
  const [otpVerified, setOtpVerified] = useState(false);
  const [sendingOtp, setSendingOtp] = useState(false);
  const [verifyingOtp, setVerifyingOtp] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [currentOtpAction, setCurrentOtpAction] = useState<'password' | 'email'>('password');
  const [passwordOtpVerified, setPasswordOtpVerified] = useState(false);
  const [emailOtpVerified, setEmailOtpVerified] = useState(false);
  
  // Payment history states
  const [payments, setPayments] = useState<any[]>([]);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentStats, setPaymentStats] = useState<any>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');

  useEffect(() => {
    const token = localStorage.getItem('dormease_token');
    if (!token) {
      setLoading(false);
      return;
    }

    fetch(`${API_BASE}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.ok ? r.json() : Promise.reject(r))
      .then((data) => {
        setUser(data.user || null);
        setLoading(false);
      })
      .catch(() => {
        setUser(null);
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  // Clear forms when opening security section
  useEffect(() => {
    if (activeTab === 'security') {
      passForm.resetFields();
      emailForm.resetFields();
      // Delay to ensure React has rendered the inputs
      setTimeout(() => {
        const emailInputs = document.querySelectorAll('input[type="email"], input[name="email"]');
        const passwordInputs = document.querySelectorAll('input[type="password"], input[name="password"]');
        emailInputs.forEach((input: any) => { input.value = ''; });
        passwordInputs.forEach((input: any) => { input.value = ''; });
      }, 100);
    }
  }, [activeTab, passForm, emailForm]);

  const handleLogout = async () => {
    const token = localStorage.getItem('dormease_token');
    try {
      await fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch {}
    localStorage.removeItem('dormease_token');
    message.success('Logged out successfully');
    window.location.href = '/';
  };

  const fetchPaymentHistory = async () => {
    const token = localStorage.getItem('dormease_token');
    if (!token) return;

    try {
      setPaymentLoading(true);
      console.log('Fetching payment history...');
      const [paymentsRes, statsRes] = await Promise.all([
        fetch(`${API_BASE}/payment-history?limit=100`, {
          headers: { Authorization: `Bearer ${token}` },
        }),
        fetch(`${API_BASE}/payment-history/stats`, {
          headers: { Authorization: `Bearer ${token}` },
        }),
      ]);

      if (paymentsRes.ok) {
        const data = await paymentsRes.json();
        console.log('Payment history received:', data);
        setPayments(data.payments || []);
      } else {
        console.error('Failed to fetch payments:', paymentsRes.status, await paymentsRes.text());
      }

      if (statsRes.ok) {
        const stats = await statsRes.json();
        console.log('Payment stats received:', stats);
        setPaymentStats(stats);
      } else {
        console.error('Failed to fetch stats:', statsRes.status, await statsRes.text());
      }
    } catch (err) {
      console.error('Error fetching payment history:', err);
      message.error('Failed to load payment history');
    } finally {
      setPaymentLoading(false);
    }
  };

  useEffect(() => {
    fetchPaymentHistory();
  }, []);

  const handleRequestOtp = async () => {
    try {
      setSendingOtp(true);
      const token = localStorage.getItem('dormease_token');
      const response = await fetch(`${API_BASE}/auth/request-change-otp`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || 'Failed to send OTP');
      }

      setOtpSent(true);
      setCountdown(60);
      message.success('OTP sent to your email!');
    } catch (error: any) {
      message.error(error.message || 'Failed to send OTP');
    } finally {
      setSendingOtp(false);
    }
  };

  const handleVerifyOtp = async (values: { otp: string }) => {
    try {
      setVerifyingOtp(true);
      const token = localStorage.getItem('dormease_token');
      const response = await fetch(`${API_BASE}/auth/verify-change-otp`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ otp: values.otp }),
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || 'Invalid OTP');
      }

      setOtpVerified(true);
      if (currentOtpAction === 'password') {
        setPasswordOtpVerified(true);
      } else {
        setEmailOtpVerified(true);
      }
      message.success('OTP verified! You can now change your ' + (currentOtpAction === 'password' ? 'password' : 'email'));
    } catch (error: any) {
      message.error(error.message || 'OTP verification failed');
    } finally {
      setVerifyingOtp(false);
    }
  };

  const handleCloseOtpModal = () => {
    setShowPasswordOtpModal(false);
    setShowEmailOtpModal(false);
    setOtpSent(false);
    setOtpVerified(false);
    otpModalForm.resetFields();
    setCountdown(0);
  };

  const handlePasswordChangeClick = async () => {
    try {
      // Validate form first
      await passForm.validateFields();
      setCurrentOtpAction('password');
      setPasswordOtpVerified(false);
      setShowPasswordOtpModal(true);
    } catch (error) {
      // Validation failed, errors are shown by antd
    }
  };

  const handleEmailChangeClick = async () => {
    try {
      // Validate form first
      await emailForm.validateFields();
      setCurrentOtpAction('email');
      setEmailOtpVerified(false);
      setShowEmailOtpModal(true);
    } catch (error) {
      // Validation failed, errors are shown by antd
    }
  };

  const avatarLetter = user?.fullName?.charAt(0).toUpperCase() || user?.username?.charAt(0)?.toUpperCase() || 'U';

  if (loading) {
    return (
      <div style={{ padding: 24, height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" tip="Loading profile..." />
      </div>
    );
  }

  return (
    <div style={{ padding: isMobile ? 12 : 24, height: '100%', background: isDarkMode ? '#0f0f0f' : '#f5f7fa', overflow: 'auto' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto', width: '100%' }}>
        <div style={{ marginBottom: 16 }}>
          <Title level={2} style={{ marginBottom: 4 }}>Account Settings</Title>
          <Text type="secondary">Manage your profile, security, and payment history</Text>
        </div>
        
        <Row gutter={[24, 24]}>
          {/* Left Sidebar */}
          <Col xs={24} lg={7}>
            {/* User Card */}
            <Card 
              style={{ 
                borderRadius: 12, 
                marginBottom: 16, 
                boxShadow: isDarkMode ? '0 2px 8px rgba(0,0,0,0.5)' : '0 2px 8px rgba(0,0,0,0.08)',
                background: isDarkMode ? '#1a1a1a' : '#fff',
                border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
              }}
              bodyStyle={{ padding: 24, textAlign: 'center' }}
            >
              <Badge.Ribbon text={user?.platform || 'web'} color="purple">
                <Avatar size={100} style={{ backgroundColor: '#7c3aed', fontSize: 42, marginBottom: 16 }}>
                  {avatarLetter}
                </Avatar>
              </Badge.Ribbon>
              <Title level={4} style={{ margin: '16px 0 4px' }}>
                {user?.fullName || 'User'}
              </Title>
              <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>@{user?.username || 'username'}</Text>
              <Tag icon={<MailOutlined />} color="blue" style={{ marginBottom: 16 }}>{user?.email || 'email@exam ple.com'}</Tag>
              
              <Divider style={{ margin: '16px 0' }} />
              
              <Button 
                type="primary" 
                icon={<EditOutlined />}
                block 
                size="large"
                style={{ marginBottom: 12 }}
                onClick={() => {
                  form.setFieldsValue({ fullName: user?.fullName, username: user?.username, email: user?.email, phoneNumber: user?.phoneNumber || '', password: '' });
                  setEditing(true);
                }}
              >
                Edit Profile
              </Button>
              
              <Button 
                danger
                icon={<LogoutOutlined />}
                block
                size="large"
                onClick={handleLogout}
              >
                Logout
              </Button>
            </Card>

            {/* Payment Stats Cards */}
            <Row gutter={[12, 12]}>
              <Col xs={12}>
                <Card 
                  style={{ 
                    borderRadius: 12, 
                    background: isDarkMode ? '#1a1a1a' : '#fff',
                    border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0',
                    boxShadow: isDarkMode ? '0 2px 8px rgba(0,0,0,0.5)' : '0 2px 8px rgba(0,0,0,0.08)',
                  }}
                  bodyStyle={{ padding: '16px' }}
                >
                  <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Payments</Text>
                  <div style={{ fontSize: 28, fontWeight: 700, color: '#7c3aed', marginTop: 8 }}>
                    {paymentStats?.stats?.total_payments || 0}
                  </div>
                </Card>
              </Col>
              <Col xs={12}>
                <Card 
                  style={{ 
                    borderRadius: 12, 
                    background: isDarkMode ? '#1a1a1a' : '#fff',
                    border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0',
                    boxShadow: isDarkMode ? '0 2px 8px rgba(0,0,0,0.5)' : '0 2px 8px rgba(0,0,0,0.08)',
                  }}
                  bodyStyle={{ padding: '16px' }}
                >
                  <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Total Paid</Text>
                  <div style={{ fontSize: 28, fontWeight: 700, color: '#22c55e', marginTop: 8 }}>
                    ₱{Number(paymentStats?.stats?.total_paid || 0).toLocaleString()}
                  </div>
                </Card>
              </Col>
            </Row>
          </Col>

          {/* Right Content Area */}
          <Col xs={24} lg={17}>
            <Card 
              style={{ 
                borderRadius: 12, 
                boxShadow: isDarkMode ? '0 2px 8px rgba(0,0,0,0.5)' : '0 2px 8px rgba(0,0,0,0.08)',
                background: isDarkMode ? '#1a1a1a' : '#fff',
                border: isDarkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0',
                minHeight: 600,
              }}
              bodyStyle={{ padding: 0 }}
            >
              <Tabs
                activeKey={activeTab}
                onChange={setActiveTab}
                style={{ padding: '0' }}
                tabBarStyle={{ 
                  paddingLeft: 24, 
                  paddingRight: 24, 
                  marginBottom: 0,
                  borderBottom: isDarkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
                }}
                items={[
                  {
                    key: 'personal',
                    label: (
                      <Space>
                        <UserOutlined />
                        <span>Personal Info</span>
                      </Space>
                    ),
                    children: (
                      <div style={{ padding: 24 }}>
                        <div style={{ marginBottom: 16 }}>
                          <Title level={4} style={{ marginBottom: 4 }}>Personal Information</Title>
                          <Text type="secondary">Your account identity & details</Text>
                        </div>
                        <Descriptions column={1} bordered size="middle">
                          <Descriptions.Item label="Full Name">
                            <Text strong>{user?.fullName || '-'}</Text>
                          </Descriptions.Item>
                          <Descriptions.Item label="Username">
                            <Text>{user?.username || '-'}</Text>
                          </Descriptions.Item>
                          <Descriptions.Item label="Email Address">
                            <Space>
                              <MailOutlined style={{ color: '#4f73ff' }} />
                              <Text>{user?.email || '-'}</Text>
                            </Space>
                          </Descriptions.Item>
                          <Descriptions.Item label="Phone Number">
                            <Text>{user?.phoneNumber ? `+63${user.phoneNumber}` : '-'}</Text>
                          </Descriptions.Item>
                          <Descriptions.Item label="Platform">
                            <Tag color="blue">{user?.platform || 'web'}</Tag>
                          </Descriptions.Item>
                          <Descriptions.Item label="Account Status">
                            <Tag icon={<CheckCircleOutlined />} color="success">Active</Tag>
                          </Descriptions.Item>
                        </Descriptions>
                        <Divider />
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Last updated: {new Date().toLocaleDateString()}
                        </Text>
                      </div>
                    ),
                  },
                  {
                    key: 'security',
                    label: (
                      <Space>
                        <LockOutlined />
                        <span>Login & Security</span>
                      </Space>
                    ),
                    children: (
                      <div style={{ padding: 24 }}>
                        <div style={{ marginBottom: 24 }}>
                          <Title level={4} style={{ marginBottom: 4 }}>Login & Security</Title>
                          <Text type="secondary">Protect your account credentials</Text>
                        </div>
                        <Row gutter={16}>
                          <Col xs={24} md={12}>
                            <Card 
                              title={
                                <Space>
                                  <LockOutlined style={{ color: '#7c3aed' }} />
                                  <span>Change Password</span>
                                </Space>
                              }
                              style={{
                                background: isDarkMode ? '#1e1e1e' : '#fafafa',
                                borderColor: isDarkMode ? '#2a2a2a' : '#d9d9d9'
                              }}
                            >
                              <Form form={passForm} layout="vertical" autoComplete="new-password"onFinish={async (vals) => {
                                if (!passwordOtpVerified) {
                                  message.error('Please verify OTP before changing password');
                                  return;
                                }
                                const { currentPassword, newPassword, confirm } = vals;
                                if (!user) return message.error('No user');
                                if (!currentPassword || !newPassword) return message.error('Please fill fields');
                                if (newPassword !== confirm) return message.error('Passwords do not match');
                                try {
                                  const loginRes = await fetch(`${API_BASE}/auth/login`, {
                                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ identifier: user.email || user.username, password: currentPassword, platform: user.platform || 'web' })
                                  });
                                  if (!loginRes.ok) return message.error('Current password incorrect');

                                  const token = localStorage.getItem('dormease_token');
                                  const patch = await fetch(`${API_BASE}/auth/me`, {
                                    method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                                    body: JSON.stringify({ password: newPassword })
                                  });
                                  if (!patch.ok) {
                                    const err = await patch.json().catch(() => ({}));
                                    throw new Error(err.message || 'Failed to update password');
                                  }
                                  message.success('Password updated');
                                  passForm.resetFields();
                                  setPasswordOtpVerified(false);
                                  handleCloseOtpModal();
                                } catch (e: any) {
                                  message.error(e.message || 'Failed to change password');
                                }
                              }}>
                                <Form.Item name="currentPassword" label="Current Password" rules={[{ required: true }]}>
                                  <Input.Password 
                                    placeholder="•••••••"
                                    autoComplete="new-password" 
                                    data-lpignore="true" 
                                    data-1p-ignore="true"
                                    data-form-type="other"
                                    name="prevent-autofill-current"
                                  />
                                </Form.Item>
                                <Form.Item name="newPassword" label="New Password" rules={[{ required: true, min: 6, message: 'Min. 6 characters' }]}>
                                  <Input.Password 
                                    placeholder="Min. 6 characters"
                                    autoComplete="new-password" 
                                    data-lpignore="true" 
                                    data-1p-ignore="true"
                                    data-form-type="other"
                                    name="prevent-autofill-new"
                                  />
                                </Form.Item>
                                <Form.Item name="confirm" label="Confirm Password" dependencies={["newPassword"]} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { if (!value || getFieldValue('newPassword') === value) return Promise.resolve(); return Promise.reject(new Error('Passwords do not match')); } })]}>
                                  <Input.Password 
                                    placeholder="•••••••"
                                    autoComplete="new-password" 
                                    data-lpignore="true" 
                                    data-1p-ignore="true"
                                    data-form-type="other"
                                    name="prevent-autofill-confirm"
                                  />
                                </Form.Item>
                                <Button 
                                  type="primary" 
                                  onClick={handlePasswordChangeClick}
                                  block
                                >
                                  Update Password
                                </Button>
                              </Form>
                            </Card>
                          </Col>
                          <Col xs={24} md={12}>
                            <Card 
                              title={
                                <Space>
                                  <MailOutlined style={{ color: '#7c3aed' }} />
                                  <span>Change Email</span>
                                </Space>
                              }
                              style={{
                                background: isDarkMode ? '#1e1e1e' : '#fafafa',
                                borderColor: isDarkMode ? '#2a2a2a' : '#d9d9d9'
                              }}
                            >
                              <Form form={emailForm} layout="vertical" autoComplete="off" data-form-type="other" onFinish={async (vals) => {
                                if (!emailOtpVerified) {
                                  message.error('Please verify OTP before changing email');
                                  return;
                                }
                                const { email, password } = vals;
                                if (!user) return message.error('No user');
                                if (!email || !password) return message.error('Please fill fields');
                                try {
                                  const loginRes = await fetch(`${API_BASE}/auth/login`, {
                                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ identifier: user.email || user.username, password, platform: user.platform || 'web' })
                                  });
                                  if (!loginRes.ok) return message.error('Password incorrect');

                                  const token = localStorage.getItem('dormease_token');
                                  const patch = await fetch(`${API_BASE}/auth/me`, {
                                    method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                                    body: JSON.stringify({ email })
                                  });
                                  if (!patch.ok) {
                                    const err = await patch.json().catch(() => ({}));
                                    throw new Error(err.message || 'Failed to update email');
                                  }
                                  const data = await patch.json();
                                  setUser(data.user || null);
                                  message.success('Email updated');
                                  emailForm.resetFields();
                                  setEmailOtpVerified(false);
                                  handleCloseOtpModal();
                                } catch (e: any) {
                                  message.error(e.message || 'Failed to update email');
                                }
                              }}>
                                <Form.Item name="email" label="New Email Address" rules={[{ required: true, type: 'email' }]}>
                                  <Input 
                                    placeholder="new@example.com"
                                    type="text" 
                                    autoComplete="off" 
                                    data-lpignore="true" 
                                    data-1p-ignore="true"
                                    data-form-type="other"
                                    name="prevent-autofill-email"
                                    prefix={<MailOutlined />}
                                  />
                                </Form.Item>
                                <Form.Item name="password" label="Current Password" rules={[{ required: true }]}>
                                  <Input.Password 
                                    placeholder="Verify identity"
                                    autoComplete="new-password" 
                                    data-lpignore="true" 
                                    data-1p-ignore="true"
                                    data-form-type="other"
                                    name="prevent-autofill-pwd"
                                  />
                                </Form.Item>
                                <Alert
                                  message="An OTP will be sent to your current email to verify this change."
                                  type="info"
                                  icon={<InfoCircleOutlined />}
                                  showIcon
                                  style={{ marginBottom: 16, fontSize: 12 }}
                                />
                                <Button 
                                  type="primary" 
                                  onClick={handleEmailChangeClick}
                                  block
                                >
                                  Update Email
                                </Button>
                              </Form>
                            </Card>
                          </Col>
                        </Row>
                      </div>
                    ),
                  },
                  {
                    key: 'payments',
                    label: (
                      <Space>
                        <CreditCardOutlined />
                        <span>Payments</span>
                      </Space>
                    ),
                    children: (
                      <div style={{ padding: 24 }}>
                        <div style={{ marginBottom: 24 }}>
                          <Title level={4} style={{ marginBottom: 4 }}>Payment History</Title>
                          <Text type="secondary">All transactions on your account</Text>
                        </div>

                        {/* Payment Stats */}
                        {paymentStats && (
                          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                            <Col xs={12} sm={6}>
                              <Card 
                                style={{ 
                                  background: isDarkMode ? '#1e1e1e' : '#f8fbff',
                                  border: 'none',
                                  borderRadius: 8,
                                  borderTop: '3px solid #7c3aed'
                                }}
                                bodyStyle={{ padding: '16px', textAlign: 'center' }}
                              >
                                <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Total</Text>
                                <div style={{ fontSize: 24, fontWeight: 700, color: '#7c3aed', marginTop: 8 }}>
                                  {paymentStats.stats?.total_payments || 0}
                                </div>
                              </Card>
                            </Col>
                            <Col xs={12} sm={6}>
                              <Card 
                                style={{ 
                                  background: isDarkMode ? '#1e1e1e' : '#f0fff4',
                                  border: 'none',
                                  borderRadius: 8,
                                  borderTop: '3px solid #22c55e'
                                }}
                                bodyStyle={{ padding: '16px', textAlign: 'center' }}
                              >
                                <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Total Paid</Text>
                                <div style={{ fontSize: 24, fontWeight: 700, color: '#22c55e', marginTop: 8 }}>
                                  ₱{Number(paymentStats.stats?.total_paid || 0).toLocaleString()}
                                </div>
                              </Card>
                            </Col>
                            <Col xs={12} sm={6}>
                              <Card 
                                style={{ 
                                  background: isDarkMode ? '#1e1e1e' : '#fef3f2',
                                  border: 'none',
                                  borderRadius: 8,
                                  borderTop: '3px solid #ef4444'
                                }}
                                bodyStyle={{ padding: '16px', textAlign: 'center' }}
                              >
                                <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Pending</Text>
                                <div style={{ fontSize: 24, fontWeight: 700, color: '#ef4444', marginTop: 8 }}>
                                  ₱{Number(paymentStats.stats?.total_pending || 0).toLocaleString()}
                                </div>
                              </Card>
                            </Col>
                            <Col xs={12} sm={6}>
                              <Card 
                                style={{ 
                                  background: isDarkMode ? '#1e1e1e' : '#fffbf5',
                                  border: 'none',
                                  borderRadius: 8,
                                  borderTop: '3px solid #fb923c'
                                }}
                                bodyStyle={{ padding: '16px', textAlign: 'center' }}
                              >
                                <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Average</Text>
                                <div style={{ fontSize: 24, fontWeight: 700, color: '#fb923c', marginTop: 8 }}>
                                  ₱{Number(paymentStats.stats?.avg_payment || 0).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                                </div>
                              </Card>
                            </Col>
                          </Row>
                        )}

                        {/* Search Input */}
                        {!paymentLoading && payments.length > 0 && (
                          <div style={{ marginBottom: 16 }}>
                            <Input
                              placeholder="Search by tenant, dorm, amount, type, or status..."
                              prefix={<SearchOutlined style={{ color: isDarkMode ? '#9ca3af' : '#8c8c8c' }} />}
                              value={searchQuery}
                              onChange={(e) => setSearchQuery(e.target.value)}
                              allowClear
                              size="large"
                              style={{
                                borderRadius: 8,
                                background: isDarkMode ? '#1e1e1e' : '#fff',
                                borderColor: isDarkMode ? '#3a3a3a' : '#d9d9d9'
                              }}
                            />
                          </div>
                        )}

                        {/* Payment Table */}
                        {paymentLoading ? (
                          <div style={{ textAlign: 'center', padding: 40 }}>
                            <Spin size="large" />
                          </div>
                        ) : payments.length === 0 ? (
                          <div style={{ textAlign: 'center', padding: 60 }}>
                            <DollarOutlined style={{ fontSize: 64, color: isDarkMode ? '#4b5563' : '#d9d9d9', marginBottom: 16 }} />
                            <div>
                              <Text type="secondary" style={{ fontSize: 16 }}>No payment history yet</Text>
                            </div>
                          </div>
                        ) : (
                          <div style={{ overflowX: 'auto' }}>
                            <Table
                              dataSource={payments.filter((payment) => {
                                if (!searchQuery) return true;
                                const query = searchQuery.toLowerCase();
                                return (
                                  payment.tenant_name?.toLowerCase().includes(query) ||
                                  payment.dorm_name?.toLowerCase().includes(query) ||
                                  payment.amount?.toString().includes(query) ||
                                  payment.payment_source?.toLowerCase().includes(query) ||
                                  payment.status?.toLowerCase().includes(query) ||
                                  payment.payment_number?.toString().includes(query) ||
                                  payment.tenant_type?.toLowerCase().includes(query)
                                );
                              })}
                              pagination={{ pageSize: 10, showSizeChanger: false }}
                              size="middle"
                              columns={[
                                {
                                  title: 'Tenant',
                                  dataIndex: 'tenant_name',
                                  key: 'tenant_name',
                                  render: (text) => <Text strong>{text}</Text>,
                                  sorter: (a, b) => a.tenant_name.localeCompare(b.tenant_name),
                                },
                                {
                                  title: 'Tenant Type',
                                  dataIndex: 'tenant_type',
                                  key: 'tenant_type',
                                  render: (type) => (
                                    <Tag color={type === 'current' ? 'green' : 'default'}>
                                      {type === 'current' ? 'Current Tenant' : 'Old Tenant'}
                                    </Tag>
                                  ),
                                },
                                {
                                  title: 'Dorm',
                                  dataIndex: 'dorm_name',
                                  key: 'dorm_name',
                                },
                                {
                                  title: 'Amount',
                                  dataIndex: 'amount',
                                  key: 'amount',
                                  render: (amount) => (
                                    <Text strong style={{ color: '#7c3aed' }}>
                                      ₱{Number(amount).toLocaleString()}
                                    </Text>
                                  ),
                                  sorter: (a, b) => Number(a.amount) - Number(b.amount),
                                },
                                {
                                  title: 'Type',
                                  dataIndex: 'payment_source',
                                  key: 'payment_source',
                                  render: (source) => {
                                    const colors: Record<string, string> = { monthly: 'blue', advance: 'purple', deposit: 'orange' };
                                    return <Tag color={colors[source] || 'default'}>{source}</Tag>;
                                  },
                                },
                                {
                                  title: 'Status',
                                  dataIndex: 'status',
                                  key: 'status',
                                  render: (status) => {
                                    const colors: Record<string, string> = { paid: 'green', pending: 'orange', overdue: 'red' };
                                    return <Tag color={colors[status] || 'default'}>{status}</Tag>;
                                  },
                                },
                                {
                                  title: 'Date',
                                  dataIndex: 'payment_date',
                                  key: 'payment_date',
                                  render: (date) => (
                                    <Text type="secondary">
                                      {new Date(date).toLocaleDateString('en-US', { 
                                        year: 'numeric', 
                                        month: 'short', 
                                        day: 'numeric' 
                                      })}
                                    </Text>
                                  ),
                                  sorter: (a, b) => new Date(a.payment_date).getTime() - new Date(b.payment_date).getTime(),
                                },
                              ]}
                              rowKey="id"
                            />
                          </div>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            </Card>
          </Col>
        </Row>

        {/* OTP Verification Modal */}
        <Modal 
          title={`Verify via OTP - ${currentOtpAction === 'password' ? 'Change Password' : 'Change Email'}`}
          open={showPasswordOtpModal || showEmailOtpModal}
          onCancel={handleCloseOtpModal}
          width={isMobile ? '95vw' : 450}
          footer={null}
        >
          {!otpVerified ? (
            <Form form={otpModalForm} layout="vertical" onFinish={handleVerifyOtp}>
              {!otpSent ? (
                <>
                  <Text style={{ display: 'block', marginBottom: 16 }}>
                    We'll send a verification code to your email address.
                  </Text>
                  <Button 
                    type="primary" 
                    block 
                    size="large"
                    loading={sendingOtp}
                    onClick={handleRequestOtp}
                  >
                    Request OTP
                  </Button>
                </>
              ) : (
                <>
                  <Text style={{ display: 'block', marginBottom: 16 }}>
                    Enter the 6-digit code sent to your email.
                  </Text>
                  <Form.Item 
                    name="otp" 
                    label="Enter OTP" 
                    rules={[{ required: true, message: 'Please enter OTP' }]}
                  >
                    <Input 
                      placeholder="000000" 
                      maxLength={6}
                      style={{ fontSize: 24, letterSpacing: 4, textAlign: 'center' }}
                    />
                  </Form.Item>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Button block onClick={handleCloseOtpModal}>
                      Cancel
                    </Button>
                    <Button 
                      type="primary" 
                      block
                      loading={verifyingOtp}
                      onClick={() => otpModalForm.submit()}
                    >
                      Verify OTP
                    </Button>
                  </Space>
                  <div style={{ textAlign: 'center', marginTop: 12 }}>
                    {countdown > 0 ? (
                      <Text type="secondary">Resend code in {countdown}s</Text>
                    ) : (
                      <Button 
                        type="link" 
                        onClick={handleRequestOtp}
                        loading={sendingOtp}
                      >
                        Resend OTP
                      </Button>
                    )}
                  </div>
                </>
              )}
            </Form>
          ) : (
            <div style={{ textAlign: 'center', paddingTop: 16 }}>
              <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 16 }}>
                ✓ OTP Verified Successfully!
              </Text>
              <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
                You can now {currentOtpAction === 'password' ? 'change your password' : 'change your email'} below.
              </Text>
              <Button 
                type="primary" 
                block 
                size="large"
                onClick={() => {
                  if (currentOtpAction === 'password') {
                    passForm.submit();
                  } else {
                    emailForm.submit();
                  }
                }}
              >
                Proceed with {currentOtpAction === 'password' ? 'Password' : 'Email'} Change
              </Button>
            </div>
          )}
        </Modal>

        {/** Edit modal for personal information */}
          <Modal title="Edit Profile" open={editing} onCancel={() => setEditing(false)} onOk={() => form.submit()} okText="Save">
            <Form form={form} layout="vertical" onFinish={async (vals) => {
              try {
                const token = localStorage.getItem('dormease_token');
                const res = await fetch(`${API_BASE}/auth/me`, {
                  method: 'PATCH',
                  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                  body: JSON.stringify(vals),
                });
                if (!res.ok) {
                  const err = await res.json().catch(() => ({}));
                  throw new Error(err.message || 'Failed to update');
                }
                const data = await res.json();
                setUser(data.user || null);
                message.success('Profile updated');
                setEditing(false);
              } catch (e: any) {
                message.error(e.message || 'Update failed');
              }
            }}>
              <Form.Item name="fullName" label="Full name" rules={[{ required: true, message: 'Please enter full name' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="username" label="Username" rules={[{ required: true, message: 'Please enter username' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: 'Please enter a valid email' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="phoneNumber" label="Phone Number">
                <Input addonBefore="+63" placeholder="9XXXXXXXXX" maxLength={10} inputMode="numeric" />
              </Form.Item>
              <Form.Item name="password" label="New password" extra="Leave blank to keep current password">
                <Input.Password />
              </Form.Item>
            </Form>
          </Modal>
      </div>
    </div>
  );
};

export default Profile;