import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Ocupa as rotas das telas das etapas E27 a E31 até elas serem implementadas. */
@Component({
  selector: 'app-placeholder-page',
  template: `
    <h1>{{ heading() }}</h1>
    <p>Tela em construção.</p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderPage {
  readonly heading = input('');
}
