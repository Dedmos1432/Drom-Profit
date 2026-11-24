import { Link, NavLink } from 'react-router';
import { Container } from '@/shared/ui';
import styles from './Header.module.css';

const mainLinks = [
  { to: '/', label: 'Моя выборка' },
  { to: '/cars/new', label: 'Добавить авто' },
  { to: '/comparison', label: 'Сравнение' },
];

export function Header() {
  return (
    <header className={styles.header}>
      <Container className={styles.row}>
        <Link to="/" className={styles.brand}>
          <span>Drom</span> Profit
        </Link>
        <nav aria-label="Основная навигация" className={styles.nav}>
          {mainLinks.map(({ to, label }) => (
            <NavLink to={to} end className={styles.navLink} key={to}>
              {label}
            </NavLink>
          ))}
          {import.meta.env.DEV && (
            <NavLink to="/ui-kit" end className={styles.navLink}>
              UI Kit
            </NavLink>
          )}
        </nav>
      </Container>
    </header>
  );
}
