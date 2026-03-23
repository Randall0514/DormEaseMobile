import React, { useState, useEffect } from 'react';
import { Layout, Typography, Form, Input, Button, message, Grid } from 'antd';
import { MailOutlined, ArrowLeftOutlined } from '@ant-design/icons';

const { Content } = Layout;
const { Title, Text } = Typography;
const { useBreakpoint } = Grid;

interface VerifyOTPProps {
  signupData: {
    fullName: string;
    username: string;
    email: string;
    phoneNumber: string;
    password: string;
  };
  onBack: () => void;
  onVerifySuccess: () => void;
}

const VerifyOTP: React.FC<VerifyOTPProps> = ({ signupData, onBack, onVerifySuccess }) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [form] = Form.useForm();
  const [otpSent, setOtpSent] = useState(false);
  const [sendingOtp, setSendingOtp] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleRequestOtp = async () => {
    try {
      setSendingOtp(true);
      const response = await fetch('http://localhost:3000/auth/request-signup-otp', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ 
          email: signupData.email, 
          username: signupData.username 
        }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        throw new Error(errorBody.message || 'Failed to send OTP');
      }

      setOtpSent(true);
      setCountdown(60);
      message.success('OTP sent to your email! Check your inbox and spam folder.');
    } catch (error: any) {
      message.error(error.message || 'Unable to send OTP. Please try again.');
    } finally {
      setSendingOtp(false);
    }
  };

  const handleVerify = async (values: { otp: string }) => {
    try {
      setVerifying(true);
      const response = await fetch('http://localhost:3000/auth/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          fullName: signupData.fullName,
          username: signupData.username,
          email: signupData.email,
          phoneNumber: signupData.phoneNumber,
          password: signupData.password,
          otp: values.otp,
          platform: 'web',
        }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        throw new Error(errorBody.message || 'Verification failed');
      }

      await response.json();
      message.success('Account created successfully! Please log in.');
      onVerifySuccess();
    } catch (error: any) {
      console.error('Verification error', error);
      message.error(error.message || 'Verification failed. Please try again.');
    } finally {
      setVerifying(false);
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
          overflowY: 'auto',
        }}
      >
        <div
          style={{
            width: '100%',
            maxWidth: 450,
            background: '#79acff',
            borderRadius: 16,
            padding: isMobile ? '20px 16px' : '32px',
            boxShadow: '0 8px 32px rgba(15, 23, 42, 0.15)',
          }}
        >
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={onBack}
            style={{ marginBottom: 16, padding: 0, color: '#1e3a5f' }}
          >
            Back to Signup
          </Button>

          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div
              style={{
                width: isMobile ? 64 : 80,
                height: isMobile ? 64 : 80,
                background: '#1e3a5f',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
              }}
            >
              <MailOutlined style={{ fontSize: isMobile ? 30 : 40, color: '#fff' }} />
            </div>
            <Title level={3} style={{ margin: 0, color: '#111827', fontWeight: 700 }}>
              Verify Your Email
            </Title>
            <Text style={{ color: '#555', fontSize: 14, display: 'block', marginTop: 8 }}>
              {signupData.email}
            </Text>
          </div>

          {!otpSent ? (
            <div style={{ textAlign: 'center' }}>
              <Text style={{ display: 'block', marginBottom: 24, color: '#333', fontSize: 14 }}>
                Click the button below to receive a verification code via email.
              </Text>
              <Button
                type="primary"
                size="large"
                block
                loading={sendingOtp}
                onClick={handleRequestOtp}
                style={{
                  borderRadius: 8,
                  height: 48,
                  background: '#1e3a5f',
                  borderColor: '#1e3a5f',
                  fontWeight: 600,
                  fontSize: 15,
                }}
              >
                Send Verification Code
              </Button>
            </div>
          ) : (
            <>
              <div
                style={{
                  background: '#e6f4ff',
                  border: '1px solid #91caff',
                  borderRadius: 8,
                  padding: 12,
                  marginBottom: 24,
                  textAlign: 'center',
                }}
              >
                <Text style={{ fontSize: 13, color: '#0958d9' }}>
                  ✓ Code sent! Check your inbox and spam folder.
                </Text>
              </div>

              <Form form={form} onFinish={handleVerify} layout="vertical">
                <Form.Item
                  label="Enter 6-Digit Code"
                  name="otp"
                  rules={[
                    { required: true, message: 'Please enter the verification code' },
                    { pattern: /^\d{6}$/, message: 'Code must be 6 digits' },
                  ]}
                >
                  <Input
                    placeholder="000000"
                    maxLength={6}
                    style={{ fontSize: isMobile ? 18 : 20, textAlign: 'center', letterSpacing: isMobile ? 5 : 8 }}
                  />
                </Form.Item>

                <Form.Item style={{ marginBottom: 12 }}>
                  <Button
                    type="primary"
                    htmlType="submit"
                    block
                    loading={verifying}
                    style={{
                      borderRadius: 8,
                      height: 48,
                      background: '#1e3a5f',
                      borderColor: '#1e3a5f',
                      fontWeight: 600,
                      fontSize: 15,
                    }}
                  >
                    Verify & Create Account
                  </Button>
                </Form.Item>

                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: 13, color: '#666' }}>
                    Didn't receive the code?{' '}
                  </Text>
                  <Button
                    type="link"
                    onClick={handleRequestOtp}
                    disabled={countdown > 0}
                    loading={sendingOtp}
                    style={{ padding: 0, fontSize: 13 }}
                  >
                    {countdown > 0 ? `Resend in ${countdown}s` : 'Resend Code'}
                  </Button>
                </div>
              </Form>
            </>
          )}
        </div>
      </Content>
    </Layout>
  );
};

export default VerifyOTP;
