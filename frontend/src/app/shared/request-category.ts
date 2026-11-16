export type Category = 'EQUIPMENT' | 'SERVICES' | 'SUPPLIES' | 'TRAVEL' | 'SOFTWARE' | 'OTHER';

/** Mesma ordem do enum do back. */
export const CATEGORIES: readonly Category[] = [
  'EQUIPMENT',
  'SERVICES',
  'SUPPLIES',
  'TRAVEL',
  'SOFTWARE',
  'OTHER',
];

export const CATEGORY_LABELS: Record<Category, string> = {
  EQUIPMENT: 'Equipamentos',
  SERVICES: 'Serviços',
  SUPPLIES: 'Materiais',
  TRAVEL: 'Viagens',
  SOFTWARE: 'Software',
  OTHER: 'Outros',
};
