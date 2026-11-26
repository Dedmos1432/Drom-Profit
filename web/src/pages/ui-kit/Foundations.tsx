import type { CSSProperties } from 'react';
import { Panel, Stack } from '@/shared/ui';
import tokens from '../../../docs/design-tokens.json';
import styles from './UiKitPage.module.css';

const palette = tokens.color.palette;
const color = tokens.color.semantic;
const typography = tokens.typography;

const swatches = [
  ['Брендовый', color.brand.accent],
  ['Основное действие', color.action.primary],
  ['Вторичное действие', color.action.secondary],
  ['Контрастное действие', color.action.contrast],
  ['Поверхность', color.background.surface],
  ['Панели', color.background.panel],
  ['Основной текст', color.text.primary],
  ['Ссылка', color.text.link],
  ['Информация', color.status.infoSurface],
  ['Успех', color.status.successSurface],
  ['Предупреждение', color.status.warningSurface],
  ['Ошибка', color.status.errorSurface],
] as const;

function colorValue(value: string) {
  const key = value.match(/^\{color\.palette\.(\w+)\}$/)?.[1] as keyof typeof palette | undefined;
  return key ? palette[key].$value : value;
}

const typeSamples = [
  ['Заголовок страницы', 'page', 700],
  ['Заголовок секции', 'section', 700],
  ['Заголовок карточки', 'cardTitle', 700],
  ['Основной текст', 'body', 400],
  ['Подпись', 'caption', 400],
] as const;

const dimensions = [
  ['Кнопка', tokens.sizing.control.$value],
  ['Сенсорная цель', tokens.sizing.touchTarget.$value],
  ['Большая кнопка', tokens.sizing.controlLarge.$value],
  ['Радиус кнопки', tokens.radius.control.$value],
  ['Радиус панели', tokens.radius.panelLarge.$value],
  ['Контур поля', tokens.border.thin.$value],
  ['Контур фокуса', tokens.border.emphasis.$value],
] as const;

export function Foundations() {
  return (
    <Stack gap="roomy">
      <div className={styles.swatches}>
        {swatches.map(([label, token]) => {
          const value = colorValue(token.$value);
          return (
            <div className={styles.swatch} key={label}>
              <div
                className={styles.swatchColor}
                style={{ backgroundColor: value }}
                aria-hidden="true"
              />
              <div className={styles.swatchLabel}>
                <strong>{label}</strong>
                <code>{value}</code>
              </div>
            </div>
          );
        })}
      </div>
      <div className={styles.columns}>
        <Panel>
          <Stack gap="tight">
            <h3>Типографика</h3>
            {typeSamples.map(([label, type, weight]) => (
              <div className={styles.typeSample} key={type}>
                <span
                  style={{
                    fontSize: typography.size[type].$value,
                    lineHeight: typography.lineHeight[type].$value,
                    fontWeight: weight,
                  }}
                >
                  {label}
                </span>
                <code>
                  {typography.size[type].$value} / {typography.lineHeight[type].$value}
                </code>
              </div>
            ))}
          </Stack>
        </Panel>
        <Panel>
          <Stack gap="tight">
            <h3>Размеры и отступы</h3>
            <div className={styles.spacingSamples}>
              {Object.entries(tokens.spacing).map(([name, token]) => (
                <div key={name}>
                  <span style={{ '--sample-size': token.$value } as CSSProperties} />
                  <code>{token.$value}</code>
                </div>
              ))}
            </div>
            <dl className={styles.dimensionList}>
              {dimensions.map(([name, value]) => (
                <div key={name}>
                  <dt>{name}</dt>
                  <dd>
                    <code>{value}</code>
                  </dd>
                </div>
              ))}
            </dl>
            <div className={styles.shadowSample}>Тень раскрывающейся панели</div>
          </Stack>
        </Panel>
      </div>
    </Stack>
  );
}
