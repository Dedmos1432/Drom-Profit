import { ChevronDown } from 'lucide-react';
import { useId } from 'react';
import type { AriaAttributes, ComponentPropsWithRef, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Fields.module.css';

type FieldContent = {
  label: ReactNode;
  helperText?: ReactNode;
  error?: ReactNode;
};

type FieldState = Pick<AriaAttributes, 'aria-describedby' | 'aria-invalid'> & {
  id?: string;
  required?: boolean;
};

type LabelProps = ComponentPropsWithRef<'label'> & { required?: boolean };

export function Label({ required, className, children, ...props }: LabelProps) {
  return (
    <label {...props} className={classes(styles.label, className)}>
      {children}
      {required && (
        <span className={styles.required} aria-hidden="true">
          {' '}
          *
        </span>
      )}
    </label>
  );
}

function useField({ id, helperText, error, ...input }: FieldContent & FieldState) {
  const generatedId = useId();
  const controlId = id ?? generatedId;
  const hintId = helperText ? controlId + '-hint' : undefined;
  const errorId = error ? controlId + '-error' : undefined;

  return {
    control: {
      id: controlId,
      'aria-describedby':
        [input['aria-describedby'], hintId, errorId].filter(Boolean).join(' ') || undefined,
      'aria-invalid': error ? true : input['aria-invalid'],
    },
    messages: (
      <>
        {helperText && (
          <div className={styles.helper} id={hintId}>
            {helperText}
          </div>
        )}
        {error && (
          <div className={styles.error} id={errorId}>
            {error}
          </div>
        )}
      </>
    ),
  };
}

export type InputProps = ComponentPropsWithRef<'input'> & FieldContent;

export function Input({ label, helperText, error, className, ...input }: InputProps) {
  const field = useField({ label, helperText, error, ...input });

  return (
    <div className={styles.field}>
      <Label htmlFor={field.control.id} required={input.required}>
        {label}
      </Label>
      <input {...input} {...field.control} className={classes(styles.control, className)} />
      {field.messages}
    </div>
  );
}

export type TextareaProps = ComponentPropsWithRef<'textarea'> & FieldContent;

export function Textarea({
  label,
  helperText,
  error,
  className,
  rows = 3,
  ...input
}: TextareaProps) {
  const field = useField({ label, helperText, error, ...input });

  return (
    <div className={styles.field}>
      <Label htmlFor={field.control.id} required={input.required}>
        {label}
      </Label>
      <textarea
        {...input}
        {...field.control}
        rows={rows}
        className={classes(styles.control, styles.textarea, className)}
      />
      {field.messages}
    </div>
  );
}

export type SelectProps = ComponentPropsWithRef<'select'> & FieldContent;

export function Select({
  label,
  helperText,
  error,
  className,
  children,
  multiple,
  ...input
}: SelectProps) {
  const field = useField({ label, helperText, error, ...input });

  return (
    <div className={styles.field}>
      <Label htmlFor={field.control.id} required={input.required}>
        {label}
      </Label>
      <div className={styles.selectWrapper}>
        <select
          {...input}
          {...field.control}
          multiple={multiple}
          className={classes(styles.control, !multiple && styles.select, className)}
        >
          {children}
        </select>
        {!multiple && <ChevronDown className={styles.chevron} aria-hidden="true" />}
      </div>
      {field.messages}
    </div>
  );
}

export type ChoiceProps = Omit<ComponentPropsWithRef<'input'>, 'type' | 'children'> & FieldContent;

function Choice({
  label,
  helperText,
  error,
  className,
  type,
  ...input
}: ChoiceProps & { type: 'checkbox' | 'radio' }) {
  const field = useField({ label, helperText, error, ...input });

  return (
    <div className={styles.choiceGroup}>
      <Label className={styles.choice} required={input.required}>
        <input
          {...input}
          {...field.control}
          type={type}
          className={classes(styles.choiceInput, className)}
        />
        <span>{label}</span>
      </Label>
      {field.messages}
    </div>
  );
}

export function Checkbox(props: ChoiceProps) {
  return <Choice {...props} type="checkbox" />;
}

export function Radio(props: ChoiceProps) {
  return <Choice {...props} type="radio" />;
}
