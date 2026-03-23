import React from 'react';
import { Layout, Menu, Badge } from 'antd';
import {
  HomeOutlined,
  SettingOutlined,
  MessageOutlined,
  UserOutlined,
} from '@ant-design/icons';

const { Sider } = Layout;

export type SectionKey = 'home' | 'settings' | 'messages' | 'profile' | 'archived';

interface SidebarProps {
  collapsed: boolean;
  onCollapse: (value: boolean) => void;
  activeSection: SectionKey;
  onSectionChange: (section: SectionKey) => void;
  isMobile?: boolean;
  unreadMessageCount?: number;
}

const Sidebar: React.FC<SidebarProps> = ({
  collapsed,
  onCollapse,
  activeSection,
  onSectionChange,
  isMobile = false,
  unreadMessageCount = 0,
}) => {
  const isDarkMode = document.body.classList.contains('dark-mode');
  
  // Define menu items in the visual order we want
  const menuItems = [
    { key: 'home', icon: <HomeOutlined />, label: 'Home' },
    {
      key: 'messages',
      icon: <MessageOutlined />,
      label: (
        <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <span>Messages</span>
          {unreadMessageCount > 0 && (
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                minWidth: '20px',
                height: '20px',
                padding: '0 6px',
                borderRadius: '10px',
                backgroundColor: '#ff4d4f',
                color: '#fff',
                fontSize: '11px',
                fontWeight: 'bold',
                marginLeft: '8px',
              }}
            >
              {unreadMessageCount > 9 ? '9+' : unreadMessageCount}
            </span>
          )}
        </span>
      ),
    },
    { key: 'profile', icon: <UserOutlined />, label: 'Profile' },
    { key: 'settings', icon: <SettingOutlined />, label: 'Settings' },
  ];

  // Top: Home, Messages
  const topItems = menuItems.filter((i) => ['home', 'messages', 'profile', 'settings'].includes(i.key));

  const handleMenuClick = (key: string) => {
    onSectionChange(key as SectionKey);
    if (isMobile) {
      onCollapse(true);
    }
  };

  const menuContent = (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'space-between' }}>
      <div>
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            fontSize: collapsed ? 18 : 20,
            letterSpacing: 0.5,
          }}
        >
          {collapsed ? 'DE' : 'DormEase'}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[activeSection]}
          onClick={({ key }) => handleMenuClick(String(key))}
          style={{ background: 'transparent', border: 'none' }}
          items={topItems}
        />
      </div>

      <div style={{ padding: '12px 0' }}>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[activeSection]}
          onClick={({ key }) => handleMenuClick(String(key))}
          style={{ background: 'transparent', border: 'none' }}
        />
      </div>
    </div>
  );

  const sidebarBackground = isDarkMode 
    ? 'linear-gradient(180deg, #1e1e1e 0%, #121212 100%)'
    : 'linear-gradient(180deg, #4f73ff 0%, #79acff 100%)';

  if (isMobile) {
    return (
      <div
        style={{
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          width: 220,
          transform: collapsed ? 'translateX(-100%)' : 'translateX(0)',
          transition: 'transform 0.25s ease, background 0.3s ease',
          background: sidebarBackground,
          zIndex: 1000,
          boxShadow: !collapsed ? '0 6px 24px rgba(0,0,0,0.25)' : undefined,
          overflowY: 'auto',
        }}
      >
        {menuContent}
      </div>
    );
  }

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={onCollapse}
      breakpoint="md"
      collapsedWidth={0}
      width={220}
      style={{
        background: sidebarBackground,
        position: 'relative',
        transition: 'background 0.3s ease',
      }}
    >
      {menuContent}
    </Sider>
  );
};

export default Sidebar;

