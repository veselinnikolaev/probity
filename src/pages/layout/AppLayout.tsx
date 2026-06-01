// src/pages/layout/AppLayout.tsx
import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { SidebarNavItem } from '../../components/probity/SidebarNavItem';
import styles from './app-layout.module.css';

const navItems = [
    { label: 'Dashboard',     icon: '📊', path: '/dashboard'   },
    { label: 'Portfolios',    icon: '💼', path: '/portfolios'  },
    { label: 'Risk Analysis', icon: '⚠️', path: '/risk'        },
    { label: 'VaR Report',    icon: '📈', path: '/var'         },
    { label: 'Monte Carlo',   icon: '🎲', path: '/monte'       },
    { label: 'Settings',      icon: '⚙️', path: '/settings'   },
];

const AppLayout: React.FC = () => {
    const navigate = useNavigate();
    const location = useLocation();

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
                <Outlet />
            </div>
        </div>
    );
};

export default AppLayout;