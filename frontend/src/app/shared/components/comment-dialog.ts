import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/** Mesmos limites do back: mínimo da `ActionPolicy` (ao exigir comentário) e tamanho da coluna. */
export const COMMENT_MIN_LENGTH = 10;
export const COMMENT_MAX_LENGTH = 2000;

export interface CommentDialogData {
  title: string;
  confirmLabel: string;
  /** Rejeitar e devolver exigem comentário; nas demais ações ele é opcional. */
  required: boolean;
  message?: string;
}

/** Resultado ao confirmar; fechar sem confirmar devolve `undefined`. */
export interface CommentDialogResult {
  comment: string;
}

/** Conta como o back: sem espaços nas pontas e cada code point (emoji) como um caractere. */
export function commentLength(value: string | null): number {
  return [...(value ?? '').trim()].length;
}

function minCommentLength(min: number): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = commentLength(control.value);
    return length >= min ? null : { minComment: { min, length } };
  };
}

@Component({
  selector: 'app-comment-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content>
        @if (data.message) {
          <p class="message">{{ data.message }}</p>
        }
        <mat-form-field class="comment">
          <mat-label>{{ data.required ? 'Comentário' : 'Comentário (opcional)' }}</mat-label>
          <textarea
            matInput
            name="comment"
            rows="4"
            formControlName="comment"
            [maxlength]="maxLength"
            cdkFocusInitial
          ></textarea>
          @if (data.required) {
            <mat-hint>Mínimo de {{ minLength }} caracteres ({{ length() }} digitados).</mat-hint>
          }
          @if (comment.hasError('minComment')) {
            <mat-error>O comentário precisa ter pelo menos {{ minLength }} caracteres.</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Voltar</button>
        <button mat-flat-button type="submit">{{ data.confirmLabel }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .comment {
      width: 100%;
    }
    .message {
      margin: 0 0 12px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommentDialog {
  protected readonly data = inject<CommentDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CommentDialog, CommentDialogResult>>(MatDialogRef);

  protected readonly minLength = COMMENT_MIN_LENGTH;
  protected readonly maxLength = COMMENT_MAX_LENGTH;
  protected readonly comment = new FormControl('', {
    nonNullable: true,
    validators: this.data.required ? [minCommentLength(COMMENT_MIN_LENGTH)] : [],
  });
  protected readonly form = new FormGroup({ comment: this.comment });

  protected length(): number {
    return commentLength(this.comment.value);
  }

  protected confirm(): void {
    if (this.comment.invalid) {
      this.comment.markAsTouched();
      return;
    }
    this.dialogRef.close({ comment: this.comment.value.trim() });
  }
}
