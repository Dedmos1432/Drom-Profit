import { Info, Pencil, Plus, X } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link as RouterLink } from 'react-router';
import {
  Alert,
  Badge,
  Breadcrumbs,
  Button,
  Checkbox,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  IconButton,
  ImagePlaceholder,
  Inline,
  Input,
  Link,
  Modal,
  Panel,
  Price,
  Radio,
  Select,
  Skeleton,
  SpecificationList,
  Spinner,
  Stack,
  Table,
  Tabs,
  Textarea,
  Tooltip,
} from '@/shared/ui';
import type { ButtonVariant, StatusTone } from '@/shared/ui';
import styles from './UiKitPage.module.css';

const buttonSamples = [
  ['primary', 'Сохранить'],
  ['secondary', 'Назад'],
  ['contrast', 'Сравнить'],
  ['outline', 'Проверить данные'],
  ['ghost', 'Посмотреть'],
  ['danger', 'Удалить'],
] as const satisfies readonly (readonly [ButtonVariant, string])[];

const feedback = [
  ['info', 'Источник — демонстрационные данные', 'Карточка содержит синтетическое объявление.'],
  ['success', 'Изменения сохранены', 'Новые сведения видны в карточке.'],
  ['warning', 'Предварительная оценка', 'Отметьте, какие расходы и преимущества учтены.'],
  ['error', 'Объявление недоступно', 'Проверьте ссылку или добавьте автомобиль вручную.'],
] as const satisfies readonly (readonly [StatusTone, string, string])[];

const specs = [
  { label: 'Марка и модель', value: 'Toyota Camry' },
  { label: 'Год выпуска', value: '2015' },
  { label: 'Пробег', value: '145 000 км' },
  { label: 'Комплектация', value: null },
];

export function ButtonsDemo() {
  const [last, setLast] = useState('');

  return (
    <Stack gap="roomy">
      <Inline gap="tight">
        {buttonSamples.map(([variant, label]) => (
          <Button key={variant} variant={variant} onClick={() => setLast(label)}>
            {label}
          </Button>
        ))}
      </Inline>
      <Inline gap="tight">
        <Button size="large" icon={<Plus />} onClick={() => setLast('Добавить автомобиль')}>
          Добавить автомобиль
        </Button>
        <IconButton
          icon={<Pencil />}
          label="Редактировать пример"
          variant="outline"
          onClick={() => setLast('Редактировать')}
        />
        <Button disabled variant="secondary">
          Недоступно
        </Button>
        <Button loading>Сохранение…</Button>
      </Inline>
      <div className={styles.fullWidthDemo}>
        <Button variant="outline" fullWidth onClick={() => setLast('Кнопка на всю ширину')}>
          Кнопка на всю ширину
        </Button>
      </div>
      {last && <p role="status">Нажата кнопка «{last}».</p>}
    </Stack>
  );
}

export function FormsDemo() {
  return (
    <div className={styles.columns}>
      <Stack>
        <Input label="Марка" required placeholder="Например, Toyota" />
        <Input label="Модель" defaultValue="Camry" helperText="Укажите модель из объявления." />
        <Input
          label="Цена, ₽"
          inputMode="numeric"
          defaultValue="0"
          error="Цена должна быть больше нуля."
        />
        <Input label="Источник" defaultValue="Демонстрационные данные" disabled />
      </Stack>
      <Stack>
        <Select label="Вид расхода" defaultValue="repair">
          <option value="repair">Ремонт</option>
          <option value="maintenance">ТО</option>
          <option value="other">Другое</option>
        </Select>
        <Textarea label="Заметка" placeholder="Что заметили на осмотре?" />
        <Checkbox label="Выбрать для сравнения" />
        <fieldset className={styles.fieldset}>
          <legend>Способ добавления</legend>
          <Inline gap="tight">
            <Radio name="add-method" label="По ссылке" defaultChecked />
            <Radio name="add-method" label="Вручную" />
          </Inline>
        </fieldset>
      </Stack>
    </div>
  );
}

export function FeedbackDemo() {
  const [retried, setRetried] = useState(false);

  return (
    <Stack gap="roomy">
      <div className={styles.columns}>
        {feedback.map(([tone, title, description]) => (
          <Alert key={tone} tone={tone} title={title}>
            {description}
          </Alert>
        ))}
      </div>
      <div className={styles.columns}>
        <Panel>
          <EmptyState
            title="Выборка пуста"
            description="Добавьте автомобиль для сравнения."
            action={<RouterLink to="/cars/new">Добавить автомобиль</RouterLink>}
          />
        </Panel>
        <Panel>
          {retried ? (
            <Alert role="status" tone="success" title="Демонстрационный запрос выполнен" />
          ) : (
            <ErrorState
              title="Не удалось загрузить карточку"
              description="Повторите запрос."
              action={
                <Button variant="secondary" onClick={() => setRetried(true)}>
                  Повторить
                </Button>
              }
            />
          )}
        </Panel>
      </div>
      <Panel>
        <Stack gap="tight">
          <Spinner label="Загрузка характеристик" />
          <Skeleton />
        </Stack>
      </Panel>
    </Stack>
  );
}

export function NavigationDemo() {
  return (
    <Stack>
      <Breadcrumbs
        items={[
          { label: 'Моя выборка', link: <RouterLink to="/">Моя выборка</RouterLink> },
          { label: 'Автомобиль' },
        ]}
      />
      <Tabs
        label="Сведения об автомобиле"
        items={[
          {
            id: 'specs',
            label: 'Характеристики',
            content: <p>Параметры, указанные в объявлении.</p>,
          },
          {
            id: 'factors',
            label: 'Расходы',
            content: <p>Необходимые вложения по оценке покупателя.</p>,
          },
          { id: 'notes', label: 'Заметки', content: <p>Дополнительные сведения с осмотра.</p> },
          { id: 'disabled', label: 'Недоступно', disabled: true, content: null },
        ]}
      />
    </Stack>
  );
}

function ComparisonExample({ caption }: { caption: string }) {
  return (
    <Table caption={caption} wide>
      <thead>
        <tr>
          <th scope="col">Показатель</th>
          <th scope="col" data-numeric="">
            Toyota Camry, 2015
          </th>
          <th scope="col" data-numeric="">
            Honda Accord, 2016
          </th>
          <th scope="col" data-numeric="">
            Mazda 6, 2017
          </th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <th scope="row">Цена объявления</th>
          <td data-numeric="">
            <Price size="compact" value={1200000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={1270000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={1390000} />
          </td>
        </tr>
        <tr>
          <th scope="row">Необходимые вложения по указанным суммам</th>
          <td data-numeric="">
            <Price size="compact" value={140000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={30000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={null} />
          </td>
        </tr>
        <tr>
          <th scope="row">Скорректированная стоимость</th>
          <td data-numeric="">
            <Price size="compact" value={1340000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={1250000} />
          </td>
          <td data-numeric="">
            <Price size="compact" value={null} />
          </td>
        </tr>
      </tbody>
    </Table>
  );
}

export function DataDemo() {
  return (
    <Stack gap="roomy">
      <Inline>
        <Price value={1250000} size="display" />
        <Price value={0} />
        <Price value={-20000} />
        <Price value={null} />
        <Badge tone="info">Демо</Badge>
        <Badge tone="warning">Расходы не подтверждены</Badge>
        <Tooltip text="Суммы указаны в целых рублях." align="end">
          <IconButton label="О денежных значениях" icon={<Info />} />
        </Tooltip>
      </Inline>
      <div className={styles.columns}>
        <Panel>
          <SpecificationList items={specs} />
        </Panel>
        <ImagePlaceholder className={styles.imageDemo} />
      </div>
      <ComparisonExample caption="Суммы по трём демонстрационным автомобилям" />
    </Stack>
  );
}

export function OverlaysDemo() {
  const [modalOpen, setModalOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  return (
    <Stack gap="tight">
      <Inline gap="tight">
        <Button variant="secondary" onClick={() => setModalOpen(true)}>
          Открыть диалог
        </Button>
        <Button
          variant="outline"
          icon={<X />}
          onClick={() => {
            setConfirmed(false);
            setConfirmOpen(true);
          }}
        >
          Проверить подтверждение
        </Button>
      </Inline>
      {confirmed && <p role="status">Демонстрационное удаление подтверждено.</p>}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Сведения об автомобиле"
        description="Диалог можно закрыть кнопкой или клавишей Escape."
      >
        <SpecificationList items={specs} />
        <div>
          <Button onClick={() => setModalOpen(false)}>Готово</Button>
        </div>
      </Modal>
      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title="Удалить автомобиль из выборки?"
        description="Демонстрационная карточка Toyota Camry будет удалена."
        confirmLabel="Удалить"
        onConfirm={async () => {
          await new Promise((resolve) => window.setTimeout(resolve, 500));
          setConfirmed(true);
        }}
      />
    </Stack>
  );
}

export function ProductExamples() {
  const [submitted, setSubmitted] = useState(false);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
  }

  return (
    <Stack gap="roomy">
      <div className={styles.columns}>
        <Panel large>
          <Stack gap="tight">
            <Inline gap="tight">
              <h3>Toyota Camry, 2015</h3>
              <Badge tone="info">Демо</Badge>
            </Inline>
            <SpecificationList items={specs} />
            <div>
              <p className={styles.secondaryText}>Цена объявления</p>
              <Price value={1200000} size="display" />
            </div>
          </Stack>
        </Panel>
        <Panel large>
          <form onSubmit={onSubmit}>
            <Stack gap="tight">
              <h3>Добавление автомобиля</h3>
              <Input
                label="Ссылка на объявление"
                name="listing-url"
                type="url"
                placeholder="https://auto.drom.ru/…"
                required
                onChange={() => setSubmitted(false)}
              />
              <Inline gap="tight">
                <Button type="submit">Проверить ввод</Button>
                <RouterLink to="/cars/new">К экрану добавления</RouterLink>
              </Inline>
              {submitted && <p role="status">Демонстрационный ввод принят.</p>}
            </Stack>
          </form>
        </Panel>
      </div>
      <Panel>
        <Stack>
          <div>
            <p className={styles.secondaryText}>Предполагаемые затраты на приобретение</p>
            <Price value={1340000} size="display" />
          </div>
          <Alert tone="warning" title="Результат предварительный">
            Суммы показаны по указанным данным. Подтвердите расходы и преимущества.
          </Alert>
          <Link href="#data">Посмотреть состояния денежных значений</Link>
        </Stack>
      </Panel>
      <ComparisonExample caption="Пример сравнения. Демонстрационные данные" />
    </Stack>
  );
}
