import React, { useState } from 'react';
import {
  Layout,
  Grid,
  Row,
  Col,
  Typography,
  Form,
  Input,
  Button,
  Checkbox,
  message,
  Modal,
  Spin,
} from 'antd';
import dormeaseLogo from './assets/dormease_logo.png';

const { Content } = Layout;
const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

const AUTH_TOKEN_KEY = 'dormease_token';

interface LoginProps {
  onNavigateToSignup?: () => void;
  onLoginSuccess?: () => void;
}

const Login: React.FC<LoginProps> = ({ onNavigateToSignup, onLoginSuccess }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [form] = Form.useForm();

  const handleSubmit = async (values: {
    username: string;
    password: string;
    remember: boolean;
  }) => {
    try {
      const response = await fetch('http://localhost:3000/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          identifier: values.username,
          password: values.password,
          platform: 'web',
        }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        throw new Error(errorBody.message || 'Login failed');
      }

      const data = await response.json();
      if (data.token) {
        localStorage.setItem(AUTH_TOKEN_KEY, data.token);
      }
      message.success('Logged in successfully.');
      if (onLoginSuccess) {
        onLoginSuccess();
      }
    } catch (error: any) {
      console.error('Login error', error);
      message.error(error.message || 'Unable to log in. Please try again.');
    }
  };

  // -- Forgot password state
  const [forgotVisible, setForgotVisible] = useState(false);
  const [forgotStep, setForgotStep] = useState<1 | 2 | 3>(1);
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotOtp, setForgotOtp] = useState('');
  const [forgotForm] = Form.useForm();

  const handleForgotPassword = () => {
    setForgotStep(1);
    setForgotEmail('');
    setForgotOtp('');
    forgotForm.resetFields();
    setForgotVisible(true);
  };

  const handleForgotStep1 = async (values: { email: string }) => {
    setForgotLoading(true);
    try {
      const res = await fetch('http://localhost:3000/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: values.email }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        message.error(data.message || 'Failed to send OTP');
        return;
      }
      setForgotEmail(values.email);
      setForgotStep(2);
      forgotForm.resetFields();
      message.success('OTP sent to your email. Check your inbox.');
    } catch {
      message.error('Network error. Please try again.');
    } finally {
      setForgotLoading(false);
    }
  };

  const handleForgotStep2 = (values: { otp: string }) => {
    setForgotOtp(values.otp);
    setForgotStep(3);
    forgotForm.resetFields();
  };

  const handleForgotStep3 = async (values: { newPassword: string; confirmPassword: string }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('Passwords do not match');
      return;
    }
    setForgotLoading(true);
    try {
      const res = await fetch('http://localhost:3000/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail, otp: forgotOtp, newPassword: values.newPassword }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        if (res.status === 400 && data.message?.toLowerCase().includes('otp')) {
          message.error(data.message);
          setForgotStep(2);
          forgotForm.resetFields();
          return;
        }
        message.error(data.message || 'Failed to reset password');
        return;
      }
      message.success('Password reset successfully. Please log in.');
      setForgotVisible(false);
    } catch {
      message.error('Network error. Please try again.');
    } finally {
      setForgotLoading(false);
    }
  };

  const forgotModalTitle =
    forgotStep === 1 ? 'Forgot Password' :
    forgotStep === 2 ? 'Enter Verification Code' :
    'Set New Password';

  const handleSignUp = () => {
    if (onNavigateToSignup) {
      onNavigateToSignup();
    }
  };

  return (
    <>
      <Layout
        style={{
          minHeight: '100vh',
          width: '100%',
          background: 'linear-gradient(135deg, #4f73ff, #79acff)',
        }}
      >
        <Content
          style={{
            width: '100%',
            margin: 0,
            padding: isMobile ? 12 : 24,
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            overflowY: 'auto',
          }}
        >
          <Row
            gutter={[0, 0]}
            style={{
              maxWidth: '1000px',
              width: '100%',
              height: 'auto',
              minHeight: isMobile ? 'auto' : 600,
              borderRadius: 12,
              boxShadow: '0 20px 60px rgba(0, 0, 0, 0.15)',
              overflow: 'hidden',
            }}
          >
            {/* Left panel */}
            <Col
              xs={24}
              md={12}
              style={{
                background: 'linear-gradient(135deg, #4f73ff 0%, #5b7fee 100%)',
                color: '#fff',
                padding: isMobile ? '28px 20px' : '60px 48px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'relative',
                overflow: 'hidden',
              }}
            >
              {/* Decorative gradient elements */}
              <div
                style={{
                  position: 'absolute',
                  top: -100,
                  right: -100,
                  width: 300,
                  height: 300,
                  borderRadius: '50%',
                  background: 'rgba(255, 255, 255, 0.08)',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  bottom: -50,
                  left: -50,
                  width: 200,
                  height: 200,
                  borderRadius: '50%',
                  background: 'rgba(255, 255, 255, 0.05)',
                }}
              />
              <div style={{ zIndex: 1, textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                <img
                  src={dormeaseLogo}
                  alt="DormEase logo"
                  style={{
                    width: isMobile ? 90 : 120,
                    height: isMobile ? 90 : 120,
                    objectFit: 'contain',
                    display: 'block',
                    marginBottom: 18,
                  }}
                />
                <Title
                  level={1}
                  style={{
                    color: '#fff',
                    marginBottom: 24,
                    fontSize: isMobile ? 34 : 42,
                    fontWeight: 700,
                    margin: 0,
                  }}
                >
                  DormEase
                </Title>
                <Text
                  style={{
                    fontSize: 16,
                    color: '#e0ecff',
                    lineHeight: 1.6,
                    fontWeight: 400,
                  }}
                >
                  Manage your dormitory accommodations with ease.
                </Text>
              </div>
            </Col>

            {/* Right panel */}
            <Col
              xs={24}
              md={12}
              style={{
                padding: isMobile ? '28px 20px' : '60px 48px',
                background: '#79acff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <div style={{ width: '100%', maxWidth: 400 }}>
                <Title
                  level={2}
                  style={{
                    textAlign: 'center',
                    marginBottom: 8,
                    color: '#111827',
                    fontSize: isMobile ? 24 : 28,
                  }}
                >
                  Welcome back
                </Title>
                <Text
                  style={{
                    display: 'block',
                    textAlign: 'center',
                    marginBottom: 32,
                    color: '#374151',
                    fontSize: 14,
                  }}
                >
                  Sign in to your account to continue
                </Text>

                <Form
                  form={form}
                  layout="vertical"
                  onFinish={handleSubmit}
                  initialValues={{ remember: false }}
                  style={{ width: '100%' }}
                >
                  <Form.Item
                    label={<span style={{ color: '#1f2937', fontWeight: 600 }}>Email or Username</span>}
                    name="username"
                    rules={[{ required: true, message: 'Please enter your email or username' }]}
                    style={{ marginBottom: 20 }}
                  >
                    <Input
                      placeholder="name@example.com"
                      size="large"
                      style={{ borderRadius: 6, borderColor: '#cbd5e1', fontSize: 14 }}
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span style={{ color: '#1f2937', fontWeight: 600 }}>Password</span>}
                    name="password"
                    rules={[{ required: true, message: 'Please enter your password' }]}
                    style={{ marginBottom: 12 }}
                  >
                    <Input.Password
                      placeholder="Enter your password"
                      size="large"
                      style={{ borderRadius: 6, borderColor: '#cbd5e1', fontSize: 14 }}
                    />
                  </Form.Item>

                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: isMobile ? 'wrap' : 'nowrap',
                      rowGap: 8,
                      marginBottom: 24,
                    }}
                  >
                    <Form.Item name="remember" valuePropName="checked" style={{ margin: 0 }}>
                      <Checkbox style={{ color: '#374151' }}>Remember me</Checkbox>
                    </Form.Item>
                    <Button
                      type="link"
                      onClick={handleForgotPassword}
                      style={{ padding: 0, color: '#4f73ff' }}
                    >
                      Forgot password?
                    </Button>
                  </div>

                  <Form.Item style={{ marginBottom: 20 }}>
                    <Button
                      type="primary"
                      htmlType="submit"
                      size="large"
                      block
                      style={{
                        borderRadius: 6,
                        height: 48,
                        background: '#4f73ff',
                        border: 'none',
                        fontSize: 15,
                        fontWeight: 600,
                      }}
                    >
                      Sign in
                    </Button>
                  </Form.Item>

                  <div style={{ textAlign: 'center' }}>
                    <Text style={{ color: '#374151' }}>Don&apos;t have an account? </Text>
                    <Button
                      type="link"
                      onClick={handleSignUp}
                      style={{ padding: 0, color: '#4f73ff', fontWeight: 600 }}
                    >
                      Create one
                    </Button>
                  </div>
                </Form>
              </div>
            </Col>
          </Row>
        </Content>
      </Layout>

      {/* Forgot Password Modal */}
      <Modal
        open={forgotVisible}
        title={forgotModalTitle}
        onCancel={() => setForgotVisible(false)}
        footer={null}
        destroyOnClose
        width={400}
      >
        <Spin spinning={forgotLoading}>
          {forgotStep === 1 && (
            <Form form={forgotForm} layout="vertical" onFinish={handleForgotStep1}>
              <Text style={{ display: 'block', marginBottom: 16, color: '#555' }}>
                Enter your registered email address and we&apos;ll send you a verification code.
              </Text>
              <Form.Item
                name="email"
                label="Email address"
                rules={[
                  { required: true, message: 'Please enter your email' },
                  { type: 'email', message: 'Please enter a valid email' },
                ]}
              >
                <Input placeholder="name@example.com" size="large" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" block style={{ background: '#4f73ff', border: 'none' }}>
                  Send OTP
                </Button>
              </Form.Item>
            </Form>
          )}

          {forgotStep === 2 && (
            <Form form={forgotForm} layout="vertical" onFinish={handleForgotStep2}>
              <Text style={{ display: 'block', marginBottom: 16, color: '#555' }}>
                A 6-digit code was sent to <strong>{forgotEmail}</strong>. Enter it below.
              </Text>
              <Form.Item
                name="otp"
                label="Verification code"
                rules={[
                  { required: true, message: 'Please enter the OTP' },
                  { len: 6, message: 'OTP must be 6 digits' },
                  { pattern: /^\d{6}$/, message: 'OTP must be numeric' },
                ]}
              >
                <Input placeholder="123456" size="large" maxLength={6} />
              </Form.Item>
              <Form.Item style={{ marginBottom: 8 }}>
                <Button type="primary" htmlType="submit" block style={{ background: '#4f73ff', border: 'none' }}>
                  Verify Code
                </Button>
              </Form.Item>
              <Button
                type="link"
                block
                onClick={() => { setForgotStep(1); forgotForm.resetFields(); }}
                style={{ padding: 0 }}
              >
                Resend / change email
              </Button>
            </Form>
          )}

          {forgotStep === 3 && (
            <Form form={forgotForm} layout="vertical" onFinish={handleForgotStep3}>
              <Text style={{ display: 'block', marginBottom: 16, color: '#555' }}>
                Choose a new password for <strong>{forgotEmail}</strong>.
              </Text>
              <Form.Item
                name="newPassword"
                label="New password"
                rules={[
                  { required: true, message: 'Please enter a new password' },
                  { min: 6, message: 'Password must be at least 6 characters' },
                ]}
              >
                <Input.Password placeholder="New password" size="large" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label="Confirm password"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: 'Please confirm your password' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('newPassword') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('Passwords do not match'));
                    },
                  }),
                ]}
              >
                <Input.Password placeholder="Confirm password" size="large" />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button type="primary" htmlType="submit" block style={{ background: '#4f73ff', border: 'none' }}>
                  Reset Password
                </Button>
              </Form.Item>
            </Form>
          )}
        </Spin>
      </Modal>
    </>
  );
};

export default Login;
