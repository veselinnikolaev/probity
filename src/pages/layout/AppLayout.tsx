import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/auth';
import { SidebarNavItem } from '@/components/probity/SidebarNavItem';
import { TopbarProvider, useTopbarDisplay } from '@/context/TopbarContext';
import styles from './app-layout.module.css';
import {ROUTES} from "@/constants/routes";

const navItems = [
    { label: 'Dashboard',     icon: '📊', path: '/dashboard'  },
    { label: 'Portfolios',    icon: '💼', path: '/portfolios' },
    { label: 'Risk Analysis', icon: '⚠️', path: '/risk'       },
    { label: 'VaR Report',    icon: '📈', path: '/var'        },
    { label: 'Monte Carlo',   icon: '🎲', path: '/monte'      },
    { label: 'Settings',      icon: '⚙️', path: '/settings'  },
];

const AppShell: React.FC = () => {
    const { topbarContent } = useTopbarDisplay();
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();

    const handleLogout = async () => {
        await logout();
        navigate(ROUTES.LOGIN, { replace: true });
    };

    return (
        <div className={styles.appShell}>
            <aside className={styles.sidebar}>
                <div className={styles.sidebarLogo}>Probity</div>
                <nav className={styles.sidebarNav}>
                    {navItems.map(item => (
                        <SidebarNavItem
                            key={item.path}
                            label={item.label}
                            icon={item.icon}
                            active={location.pathname.startsWith(item.path)}
                            onClick={() => navigate(item.path)}
                        />
                    ))}
                </nav>
            </aside>

            <div className={styles.mainWrapper}>
                <header className={styles.topbar}>
                    <div className={styles.topbarSlot}>
                        {topbarContent}
                    </div>
                    <div className={styles.topbarActions}>
                        <span className={styles.notificationBell}>🔔</span>
                        <div
                            className={styles.userAvatar}
                            onClick={handleLogout}
                            title="Click to sign out"
                        >
                            {user?.username?.charAt(0).toUpperCase() || 'U'}
                        </div>
                    </div>
                </header>

                <main className={styles.pageContent}>
                    <Outlet />
                </main>
            </div>
        </div>
    );
};

const AppLayout: React.FC = () => (
    <TopbarProvider>
        <AppShell />
    </TopbarProvider>
);

export default AppLayout;