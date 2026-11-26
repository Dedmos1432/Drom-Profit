import { Link, PageHeader, Section } from '@/shared/ui';
import { Foundations } from './Foundations';
import {
  ButtonsDemo,
  DataDemo,
  FeedbackDemo,
  FormsDemo,
  NavigationDemo,
  OverlaysDemo,
  ProductExamples,
} from './UiKitDemos';
import styles from './UiKitPage.module.css';

const showcases = [
  { id: 'foundations', title: 'Токены', Example: Foundations },
  { id: 'buttons', title: 'Кнопки', Example: ButtonsDemo },
  { id: 'forms', title: 'Формы', Example: FormsDemo },
  { id: 'feedback', title: 'Обратная связь', Example: FeedbackDemo },
  { id: 'navigation', title: 'Навигация', Example: NavigationDemo },
  { id: 'data', title: 'Представление данных', Example: DataDemo },
  { id: 'overlays', title: 'Диалоги', Example: OverlaysDemo },
  { id: 'product', title: 'Примеры для продукта', Example: ProductExamples },
];

export default function UiKitPage() {
  return (
    <div className={styles.page}>
      <PageHeader title="UI Kit" description="Компоненты и дизайн-токены «Дром Профит»." />
      <div className={styles.guide}>
        <nav className={styles.index} aria-label="Разделы UI Kit">
          <ul>
            {showcases.map(({ id, title }) => (
              <li key={id}>
                <Link href={'#' + id}>{title}</Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className={styles.sections}>
          {showcases.map(({ id, title, Example }) => (
            <Section className={styles.section} id={id} title={title} key={id}>
              <Example />
            </Section>
          ))}
        </div>
      </div>
    </div>
  );
}
