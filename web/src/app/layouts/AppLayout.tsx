import { useEffect, useRef } from 'react';
import { Outlet, useLocation } from 'react-router';
import { Container, Link } from '@/shared/ui';
import { Header } from '@/widgets/header/Header';
import { classes } from '@/shared/lib/classes';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const { pathname } = useLocation();
  const previous = useRef(pathname);
  const main = useRef<HTMLElement>(null);

  useEffect(() => {
    if (pathname === previous.current) return;
    previous.current = pathname;
    main.current?.focus();
  }, [pathname]);

  return (
    <>
      <Link href="#main-content" className={styles.skip}>
        К основному содержимому
      </Link>
      <Header />
      <main
        id="main-content"
        tabIndex={-1}
        ref={main}
        className={classes(styles.main, pathname !== '/ui-kit' && styles.productMain)}
      >
        <Container>
          <Outlet />
        </Container>
      </main>
    </>
  );
}
