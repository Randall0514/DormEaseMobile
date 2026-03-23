import React, { useState } from 'react';
import { Layout, Typography, Form, Input, Checkbox, Button, Grid } from 'antd';
import TermsModal from './components/TermsModal';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';

const { Content } = Layout;
const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

interface SignupProps {
  onNavigateToLogin?: () => void;
  onContinueToVerify?: (data: { fullName: string; username: string; email: string; phoneNumber: string; password: string }) => void;
}

const Signup: React.FC<SignupProps> = ({ onNavigateToLogin, onContinueToVerify }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [form] = Form.useForm();
  const [password, setPassword] = useState('');
  const [showPasswordRequirements, setShowPasswordRequirements] = useState(false);
  const [termsVisible, setTermsVisible] = useState(false);
  const [agreeChecked, setAgreeChecked] = useState(false);

  const passwordRequirements = {
    minLength: password.length >= 8,
    hasUpperCase: /[A-Z]/.test(password),
    hasLowerCase: /[a-z]/.test(password),
    hasNumber: /\d/.test(password),
  };

  const allRequirementsMet = Object.values(passwordRequirements).every(req => req);

  const handleSubmit = async (values: {
    fullName: string;
    username: string;
    email: string;
    phoneNumber: string;
    password: string;
    confirmPassword: string;
    agree: boolean;
  }) => {
    if (onContinueToVerify) {
      onContinueToVerify({
        fullName: values.fullName,
        username: values.username,
        email: values.email,
        phoneNumber: values.phoneNumber,
        password: values.password,
      });
    }
  };

  const handleBackToLogin = () => {
    if (onNavigateToLogin) {
      onNavigateToLogin();
    }
  };

  return (
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
          padding: isMobile ? 12 : 20,
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflowY: 'auto', // allow scrolling inside signup only
        }}
      >
        <div
          style={{
            width: '100%',
            maxWidth: 500,
            background: '#79acff',
            borderRadius: 16,
            padding: isMobile ? '20px 16px' : '24px 28px',
            boxShadow: '0 8px 32px rgba(15, 23, 42, 0.15)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
          }}
        >
          <div style={{ textAlign: 'center', marginBottom: 16 }}>
            <Title level={3} style={{ margin: 0, color: '#111827', fontWeight: 700, letterSpacing: 0.3 }}>
              Create Account
            </Title>
          </div>

          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ agree: false }}
            size="large"
          >
            <Form.Item
              label="Full Name"
              name="fullName"
              rules={[
                { required: true, message: 'Please enter your full name' },
                {
                  min: 2,
                  message: 'Full name must be at least 2 characters',
                },
                {
                  pattern: /^[a-zA-Z\s'-]+$/,
                  message: 'Full name can only contain letters, spaces, hyphens, and apostrophes',
                },
              ]}
              style={{ marginBottom: 12 }}
            >
              <Input placeholder="Enter your full name" />
            </Form.Item>

            <Form.Item
              label="Username"
              name="username"
              rules={[
                { required: true, message: 'Please choose a username' },
                {
                  min: 3,
                  message: 'Username must be at least 3 characters',
                },
                {
                  max: 20,
                  message: 'Username must be no more than 20 characters',
                },
                {
                  pattern: /^[a-zA-Z0-9_]+$/,
                  message: 'Username can only contain letters, numbers, and underscores',
                },
              ]}
              style={{ marginBottom: 12 }}
            >
              <Input placeholder="Choose a username" />
            </Form.Item>

            <Form.Item
              label="Email Address"
              name="email"
              rules={[
                { required: true, message: 'Please enter your email' },
                { type: 'email', message: 'Please enter a valid email address' },
              ]}
              style={{ marginBottom: 12 }}
            >
              <Input placeholder="Enter your email" />
            </Form.Item>

            <Form.Item
              label="Phone Number"
              name="phoneNumber"
              rules={[
                {
                  pattern: /^[0-9]{10,11}$/,
                  message: 'Please enter a valid phone number (10-11 digits)',
                },
              ]}
              style={{ marginBottom: 12 }}
            >
              <Input addonBefore="+63" placeholder="9XXXXXXXXX" maxLength={10} inputMode="numeric" onChange={(e) => { const v = e.target.value.replace(/\D/g, '').replace(/^0+/, ''); if (v !== e.target.value) form.setFieldsValue({ phoneNumber: v }); }} />
            </Form.Item>

            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Please create a password' }]}
              style={{ marginBottom: 12 }}
            >
              <Input.Password
                placeholder="Create a password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onFocus={() => setShowPasswordRequirements(true)}
                onBlur={() => setShowPasswordRequirements(false)}
              />
            </Form.Item>

            {showPasswordRequirements && (
              <div
                style={{
                  background: '#f9f9f9',
                  border: '1px solid #e8e8e8',
                  borderRadius: 8,
                  padding: 10,
                  marginBottom: 12,
                  marginTop: -4,
                  animation: 'slideDown 0.3s ease-out',
                  opacity: 1,
                  transform: 'translateY(0)',
                  transition: 'all 0.3s ease-out',
                }}
              >
                <style>{`
                  @keyframes slideDown {
                    from {
                      opacity: 0;
                      transform: translateY(-10px);
                    }
                    to {
                      opacity: 1;
                      transform: translateY(0);
                    }
                  }
                `}</style>
                <div style={{ marginBottom: 6, fontSize: 11, fontWeight: 700, color: '#333', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Requirements:
                </div>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: 5 }}>
                  {passwordRequirements.minLength ? (
                    <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6, fontSize: 12 }} />
                  ) : (
                    <CloseCircleOutlined style={{ color: '#ff4d4f', marginRight: 6, fontSize: 12 }} />
                  )}
                  <span style={{ fontSize: 11 }}>8+ characters</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: 5 }}>
                  {passwordRequirements.hasUpperCase ? (
                    <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6, fontSize: 12 }} />
                  ) : (
                    <CloseCircleOutlined style={{ color: '#ff4d4f', marginRight: 6, fontSize: 12 }} />
                  )}
                  <span style={{ fontSize: 11 }}>Uppercase (A-Z)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: 5 }}>
                  {passwordRequirements.hasLowerCase ? (
                    <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6, fontSize: 12 }} />
                  ) : (
                    <CloseCircleOutlined style={{ color: '#ff4d4f', marginRight: 6, fontSize: 12 }} />
                  )}
                  <span style={{ fontSize: 11 }}>Lowercase (a-z)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  {passwordRequirements.hasNumber ? (
                    <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6, fontSize: 12 }} />
                  ) : (
                    <CloseCircleOutlined style={{ color: '#ff4d4f', marginRight: 6, fontSize: 12 }} />
                  )}
                  <span style={{ fontSize: 11 }}>Number (0-9)</span>
                </div>
              </div>
            )}

            <Form.Item
              label="Confirm Password"
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: 'Please confirm your password' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('The two passwords do not match'));
                  },
                }),
              ]}
              style={{ marginBottom: 12 }}
            >
              <Input.Password placeholder="Confirm your password" />
            </Form.Item>

            <Form.Item
              name="agree"
              valuePropName="checked"
              style={{ marginBottom: 16 }}
              rules={[
                {
                  validator: (_, value) => (value ? Promise.resolve() : Promise.reject(new Error('You must agree to the terms to continue'))),
                },
              ]}
            >
              <Checkbox
                style={{ fontSize: 13 }}
                onChange={(e) => {
                  setAgreeChecked(e.target.checked);
                  form.setFieldsValue({ agree: e.target.checked });
                }}
              >
                I agree to the{' '}
                <Text underline style={{ color: '#1f3fd1', cursor: 'pointer' }} onClick={() => setTermsVisible(true)}>
                  Terms of Service
                </Text>{' '}
                and{' '}
                <Text underline style={{ color: '#1f3fd1', cursor: 'pointer' }} onClick={() => setTermsVisible(true)}>
                  Privacy Policy
                </Text>
              </Checkbox>
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                style={{
                  borderRadius: 8,
                  height: 40,
                  background: '#1e3a5f',
                  borderColor: '#1e3a5f',
                  fontWeight: 600,
                  fontSize: 13,
                }}
                disabled={!agreeChecked || !allRequirementsMet}
              >
                Verify Email
              </Button>
            </Form.Item>

            <div style={{ textAlign: 'center', marginTop: 12 }}>
              <Text style={{ fontSize: 13, color: '#666' }}>Already have an account? </Text>
              <Button type="link" onClick={handleBackToLogin} style={{ padding: 0, fontSize: 13 }}>
                Log in
              </Button>
            </div>
          </Form>

          <TermsModal
            visible={termsVisible}
            onClose={() => setTermsVisible(false)}
            onAgree={() => {
              setTermsVisible(false);
              setAgreeChecked(true);
              form.setFieldsValue({ agree: true });
            }}
          />
        </div>
      </Content>
    </Layout>
  );
};

export default Signup;

