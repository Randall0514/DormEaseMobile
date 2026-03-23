import React from 'react';
import { Modal, Button, Typography } from 'antd';

const { Title, Paragraph } = Typography;

interface TermsModalProps {
  visible: boolean;
  onClose: () => void;
  onAgree: () => void;
}

const TermsModal: React.FC<TermsModalProps> = ({ visible, onClose, onAgree }) => {
  return (
    <Modal
      title="Terms of Service & Privacy Policy"
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          Close
        </Button>,
        <Button key="agree" type="primary" onClick={onAgree}>
          I Agree
        </Button>,
      ]}
      width={760}
      bodyStyle={{ maxHeight: '60vh', overflowY: 'auto' }}
    >
      <div style={{ paddingRight: 8 }}>
        <Title level={4}>Terms of Service</Title>
        <Paragraph>
          Welcome to DormEase. By using our service you agree to the following terms. You must be
          at least 18 years old to create an account. You are responsible for maintaining the
          confidentiality of your account and password and for all activities that occur under
          your account.
        </Paragraph>
        <Paragraph>
          You agree not to use DormEase to post illegal, harmful, abusive, or infringing content.
          DormEase may suspend or terminate accounts that violate these terms.
        </Paragraph>

        <Title level={4}>Privacy Policy</Title>
        <Paragraph>
          We collect basic account information (name, username, email) and information related to
          dorm listings and reservations. We use this information to provide and improve our
          services, to communicate with you, and to comply with legal obligations.
        </Paragraph>
        <Paragraph>
          We will not sell your personal information. We may share information with service
          providers who perform services on our behalf and as required by law. For details about
          data retention, security, and your rights, contact support.
        </Paragraph>

        <Title level={5}>Contact</Title>
        <Paragraph>If you have questions about these terms or your data, contact support@dormease.example</Paragraph>
      </div>
    </Modal>
  );
};

export default TermsModal;
