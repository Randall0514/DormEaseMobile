import React, { useEffect, useState } from 'react';
import { 
  Card, 
  Switch, 
  Typography, 
  Row, 
  Col, 
  Space, 
  Divider, 
  Modal,
  Button,
  message,
  Badge,
  Tag,
  Grid,
} from 'antd';
import {
  InfoCircleOutlined,
  BgColorsOutlined,
  QuestionCircleOutlined,
  MoonOutlined,
  SunOutlined,
  CheckCircleOutlined,
  HomeOutlined,
  ThunderboltOutlined,
  SafetyOutlined,
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;
const { useBreakpoint } = Grid;

const Settings: React.FC = () => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const [darkMode, setDarkMode] = useState<boolean>(() => {
    const stored = localStorage.getItem('dormease_darkMode');
    if (stored !== null) return stored === 'true';
    return false;
  });

  const [aboutModal, setAboutModal] = useState(false);

  useEffect(() => {
    if (darkMode) {
      document.body.classList.add('dark-mode');
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.body.classList.remove('dark-mode');
      document.documentElement.setAttribute('data-theme', 'light');
    }
    localStorage.setItem('dormease_darkMode', String(darkMode));
  }, [darkMode]);

  const handleDarkModeToggle = (checked: boolean) => {
    setDarkMode(checked);
    message.success(checked ? 'Dark mode enabled' : 'Light mode enabled');
  };

  return (
    <div style={{ padding: isMobile ? 12 : 24, height: '100%', display: 'flex', flexDirection: 'column', background: darkMode ? '#0f0f0f' : '#f5f7fa', transition: 'background 0.3s ease', overflowX: 'hidden' }}>
      <div style={{ maxWidth: 1000, margin: '0 auto', flex: 1, overflowY: 'auto', overflowX: 'hidden', width: '100%' }}>
        <Title level={2} style={{ marginBottom: 8, color: darkMode ? '#fff' : '#000' }}>Settings</Title>
        <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
          Manage your application preferences
        </Text>
        
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          {/* Appearance Settings */}
          <Card
            style={{ 
              borderRadius: 16, 
              boxShadow: darkMode 
                ? '0 4px 12px rgba(0,0,0,0.5)' 
                : '0 4px 12px rgba(0,0,0,0.08)',
              background: darkMode ? '#1a1a1a' : '#fff',
              border: darkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
            }}
          >
            <Row justify="space-between" align="middle" gutter={[16, 16]}>
              <Col xs={24} md={16}>
                <Space size="large" align="start">
                  <div style={{ 
                    width: 64, 
                    height: 64, 
                    borderRadius: 16, 
                    background: darkMode 
                      ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
                      : 'linear-gradient(135deg, #4f73ff 0%, #6c5ce7 100%)',
                    display: 'flex', 
                    alignItems: 'center', 
                    justifyContent: 'center',
                    boxShadow: darkMode 
                      ? '0 8px 16px rgba(102, 126, 234, 0.3)'
                      : '0 8px 16px rgba(79, 115, 255, 0.2)'
                  }}>
                    {darkMode ? 
                      <MoonOutlined style={{ fontSize: 32, color: '#fff' }} /> : 
                      <SunOutlined style={{ fontSize: 32, color: '#fff' }} />
                    }
                  </div>
                  <div>
                    <Title level={4} style={{ margin: 0, color: darkMode ? '#fff' : '#000' }}>
                      {darkMode ? 'Dark Mode' : 'Light Mode'}
                    </Title>
                    <Text type="secondary" style={{ fontSize: 14, display: 'block', marginTop: 4 }}>
                      {darkMode 
                        ? 'Easier on the eyes in low light conditions'
                        : 'Bright and clear interface for daytime use'
                      }
                    </Text>
                    <Space style={{ marginTop: 12 }}>
                      <Tag 
                        icon={darkMode ? <MoonOutlined /> : <SunOutlined />} 
                        color={darkMode ? 'purple' : 'gold'}
                      >
                        {darkMode ? 'Dark Theme Active' : 'Light Theme Active'}
                      </Tag>
                      <Tag icon={<CheckCircleOutlined />} color="success">
                        Auto-saved
                      </Tag>
                    </Space>
                  </div>
                </Space>
              </Col>
              <Col xs={24} md={8} style={{ textAlign: 'right' }}>
                <Switch 
                  checked={darkMode} 
                  onChange={handleDarkModeToggle}
                  checkedChildren={<MoonOutlined />}
                  unCheckedChildren={<SunOutlined />}
                  size="default"
                />
              </Col>
            </Row>
          </Card>

          {/* Features Info */}
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Card
                style={{ 
                  borderRadius: 12, 
                  textAlign: 'center',
                  background: darkMode ? '#1a1a1a' : '#fff',
                  border: darkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
                }}
                bodyStyle={{ padding: 24 }}
              >
                <ThunderboltOutlined style={{ fontSize: 40, color: '#faad14', marginBottom: 12 }} />
                <Title level={5} style={{ margin: '12px 0 8px', color: darkMode ? '#fff' : '#000' }}>
                  Performance
                </Title>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Optimized rendering for smooth experience
                </Text>
              </Card>
            </Col>
            <Col xs={24} md={8}>
              <Card
                style={{ 
                  borderRadius: 12, 
                  textAlign: 'center',
                  background: darkMode ? '#1a1a1a' : '#fff',
                  border: darkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
                }}
                bodyStyle={{ padding: 24 }}
              >
                <SafetyOutlined style={{ fontSize: 40, color: '#52c41a', marginBottom: 12 }} />
                <Title level={5} style={{ margin: '12px 0 8px', color: darkMode ? '#fff' : '#000' }}>
                  Eye Comfort
                </Title>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Reduces eye strain in low-light environments
                </Text>
              </Card>
            </Col>
            <Col xs={24} md={8}>
              <Card
                style={{ 
                  borderRadius: 12, 
                  textAlign: 'center',
                  background: darkMode ? '#1a1a1a' : '#fff',
                  border: darkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
                }}
                bodyStyle={{ padding: 24 }}
              >
                <BgColorsOutlined style={{ fontSize: 40, color: '#1890ff', marginBottom: 12 }} />
                <Title level={5} style={{ margin: '12px 0 8px', color: darkMode ? '#fff' : '#000' }}>
                  Personalized
                </Title>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Your preference saved automatically
                </Text>
              </Card>
            </Col>
          </Row>

          {/* About & Help */}
          <Card
            title={
              <Space>
                <InfoCircleOutlined style={{ color: '#4f73ff', fontSize: 18 }} />
                <Text strong style={{ fontSize: 16, color: darkMode ? '#fff' : '#000' }}>Information & Support</Text>
              </Space>
            }
            style={{ 
              borderRadius: 12, 
              boxShadow: darkMode 
                ? '0 4px 12px rgba(0,0,0,0.5)' 
                : '0 4px 12px rgba(0,0,0,0.08)',
              background: darkMode ? '#1a1a1a' : '#fff',
              border: darkMode ? '1px solid #2a2a2a' : '1px solid #f0f0f0'
            }}
          >
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Row justify="space-between" align="middle">
                <Col>
                  <Space>
                    <InfoCircleOutlined style={{ fontSize: 20, color: '#4f73ff' }} />
                    <div>
                      <Text strong style={{ color: darkMode ? '#fff' : '#000' }}>About DormEase</Text>
                      <div><Text type="secondary" style={{ fontSize: 12 }}>App version and information</Text></div>
                    </div>
                  </Space>
                </Col>
                <Col>
                  <Button onClick={() => setAboutModal(true)}>View Details</Button>
                </Col>
              </Row>

              <Divider style={{ margin: '12px 0', borderColor: darkMode ? '#1a1a1a' : '#f0f0f0' }} />

              <Row justify="space-between" align="middle">
                <Col>
                  <Space>
                    <QuestionCircleOutlined style={{ fontSize: 20, color: '#1890ff' }} />
                    <div>
                      <Text strong style={{ color: darkMode ? '#fff' : '#000' }}>Help & Support</Text>
                      <div><Text type="secondary" style={{ fontSize: 12 }}>Get assistance and documentation</Text></div>
                    </div>
                  </Space>
                </Col>
                <Col>
                  <Button onClick={() => message.info('Support documentation coming soon!')}>Get Help</Button>
                </Col>
              </Row>
            </Space>
          </Card>

          {/* Status Footer */}
          <Card 
            style={{ 
              borderRadius: 12, 
              background: darkMode 
                ? 'linear-gradient(135deg, #0a0a0a 0%, #1a1a1a 100%)'
                : 'linear-gradient(135deg, #f0f5ff 0%, #e6f0ff 100%)',
              border: darkMode ? '1px solid #1a1a1a' : '1px solid #d6e4ff'
            }}
            bodyStyle={{ padding: 16 }}
          >
            <Row justify="space-between" align="middle">
              <Col>
                <Space>
                  <CheckCircleOutlined style={{ fontSize: 18, color: '#52c41a' }} />
                  <div>
                    <Text strong style={{ color: darkMode ? '#fff' : '#000', fontSize: 14 }}>
                      All Settings Saved
                    </Text>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Last updated: {new Date().toLocaleString()}
                      </Text>
                    </div>
                  </div>
                </Space>
              </Col>
              <Col>
                <Badge 
                  status={darkMode ? 'processing' : 'success'} 
                  text={
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      {darkMode ? 'Dark Mode Active' : 'Light Mode Active'}
                    </Text>
                  } 
                />
              </Col>
            </Row>
          </Card>
        </Space>

        {/* About Modal */}
        <Modal
          title={
            <Space>
              <InfoCircleOutlined style={{ color: '#4f73ff', fontSize: 24 }} />
              <Text strong style={{ fontSize: 18 }}>About DormEase</Text>
            </Space>
          }
          open={aboutModal}
          onCancel={() => setAboutModal(false)}
          footer={[
            <Button key="close" type="primary" onClick={() => setAboutModal(false)}>
              Close
            </Button>
          ]}
          width={600}
        >
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <div style={{
                width: 80,
                height: 80,
                margin: '0 auto 16px',
                borderRadius: 20,
                background: 'linear-gradient(135deg, #4f73ff 0%, #6c5ce7 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 8px 24px rgba(79, 115, 255, 0.3)'
              }}>
                <HomeOutlined style={{ fontSize: 40, color: '#fff' }} />
              </div>
              <Title level={3} style={{ margin: '12px 0 4px' }}>DormEase</Title>
              <Badge count="v1.0.0" style={{ backgroundColor: '#4f73ff' }} />
            </div>
            
            <Divider />
            
            <Paragraph>
              <Text strong>DormEase</Text> is a modern dormitory management platform that streamlines 
              the process of finding and managing student accommodations. Built with cutting-edge 
              technology for a seamless experience.
            </Paragraph>
            
            <Paragraph>
              <Text strong>Key Features:</Text>
              <ul style={{ marginTop: 8, paddingLeft: 20 }}>
                <li>Intuitive dorm listing and management</li>
                <li>Smart reservation system with approval workflow</li>
                <li>Real-time notifications and updates</li>
                <li>Comprehensive user profile management</li>
                <li>Multi-platform support (Web & Mobile)</li>
                <li>Dark mode for comfortable viewing</li>
              </ul>
            </Paragraph>

            <Card style={{ background: '#f0f5ff', border: '1px solid #d6e4ff' }}>
              <Space direction="vertical">
                <Text strong style={{ color: '#4f73ff' }}>Technology Stack</Text>
                <Space wrap>
                  <Tag color="blue">React 19</Tag>
                  <Tag color="blue">TypeScript</Tag>
                  <Tag color="blue">Ant Design</Tag>
                  <Tag color="green">Node.js</Tag>
                  <Tag color="green">Express</Tag>
                  <Tag color="cyan">PostgreSQL</Tag>
                </Space>
              </Space>
            </Card>

            <Divider />

            <Space direction="vertical">
              <Text type="secondary" style={{ fontSize: 12 }}>
                © 2026 DormEase. All rights reserved.
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Developed with ❤️ for better student living
              </Text>
            </Space>
          </Space>
        </Modal>
      </div>
    </div>
  );
};

export default Settings;