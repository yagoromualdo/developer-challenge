/** Id da rota (`/requests/:id`) como inteiro positivo; qualquer outra coisa vira `null` e a tela mostra 404. */
export function parseRouteId(raw: string | null): number | null {
  const id = Number(raw);
  return raw !== null && Number.isInteger(id) && id > 0 ? id : null;
}
