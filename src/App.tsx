import { useState, useEffect } from 'react';
import './App.css';
import Login from './Login';
import Signup from './Signup';
import VerifyOTP from './VerifyOTP';
import Dashboard from './Dashboard';
import { WebSocketProvider } from './contexts/WebSocketContext';

const AUTH_TOKEN_KEY = 'dormease_token';
const API_BASE = 'http://localhost:3000';

type View = 'login' | 'signup' | 'verifyOtp' | 'dashboard';

interface SignupData {
  fullName: string;
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
}

function App() {
  const [view, setView] = useState<View>('login');
  const [isNewAccount, setIsNewAccount] = useState(false);
  const [sessionChecked, setSessionChecked] = useState(false);
  const [signupData, setSignupData] = useState<SignupData | null>(null);

  useEffect(() => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) {
      setSessionChecked(true);
      return;
    }
    fetch(`${API_BASE}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (res.ok) {
          setView('dashboard');
        } else {
          localStorage.removeItem(AUTH_TOKEN_KEY);
        }
      })
      .catch(() => localStorage.removeItem(AUTH_TOKEN_KEY))
      .finally(() => setSessionChecked(true));
  }, []);

  const handleLogout = async () => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token) {
      try {
        await fetch(`${API_BASE}/auth/logout`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch {
        // ignore
      }
      localStorage.removeItem(AUTH_TOKEN_KEY);
    }
    setView('login');
  };

  if (!sessionChecked) {
    return null; // or a loading spinner
  }

  if (view === 'dashboard') {
    return (
      <WebSocketProvider>
        <Dashboard
          onLogout={handleLogout}
          account={{ isNew: isNewAccount }}
          onSetupComplete={() => setIsNewAccount(false)}
        />
      </WebSocketProvider>
    );
  }

  if (view === 'signup') {
    return (
      <Signup
        onNavigateToLogin={() => setView('login')}
        onContinueToVerify={(data) => {
          setSignupData(data);
          setView('verifyOtp');
        }}
      />
    );
  }

  if (view === 'verifyOtp' && signupData) {
    return (
      <VerifyOTP
        signupData={signupData}
        onBack={() => setView('signup')}
        onVerifySuccess={() => {
          setSignupData(null);
          setView('login');
        }}
      />
    );
  }

  return (
    <Login
      onNavigateToSignup={() => setView('signup')}
      onLoginSuccess={() => {
        setIsNewAccount(false);
        setView('dashboard');
      }}
    />
  );
}

export default App;
