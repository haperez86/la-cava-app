/** Producto tal como lo devuelve GET /api/v1/products (docs/03-api.md, Menú). */
export interface Product {
  readonly id: number;
  readonly name: string;
  readonly categoryId: number;
  readonly categoryName: string;
  readonly price: number;
  readonly description: string | null;
  readonly icon: string | null;
  readonly minStock: number;
  readonly active: boolean;
  readonly seasonName: string | null;
  /** 'YYYY-MM-DD' o null. */
  readonly seasonUntil: string | null;
  /** Existencia vigente. */
  readonly available: number;
  /** Estado de disponibilidad calculado por el backend (HU-03). */
  readonly state: ProductState;
  /** Verdadero solo si está activo, en temporada y con existencia vigente > 0. */
  readonly orderable: boolean;
  readonly blockReason: BlockReason | null;
}

export type ProductState = 'ON_MENU' | 'OUT_OF_STOCK' | 'OUT_OF_SEASON' | 'INACTIVE';
export type BlockReason = 'Retirado de la carta' | 'Fuera de temporada' | 'Agotado';

/** Valores del filtro `state` de GET /api/v1/products. */
export type ProductStateFilter = 'ON_MENU' | 'OUT_OF_STOCK' | 'SEASONAL' | 'OUT_OF_SEASON' | 'INACTIVE';

export interface Category {
  readonly id: number;
  readonly name: string;
}

/** Cuerpo de PUT /api/v1/products/{id}. */
export interface ProductRequest {
  readonly name: string;
  readonly categoryName: string;
  readonly price: number;
  readonly description: string;
  readonly icon: string;
  readonly minStock: number;
  readonly seasonName: string | null;
  readonly seasonUntil: string | null;
}

/** Cuerpo de POST /api/v1/products: además puede traer el lote inicial (CA4). */
export interface CreateProductRequest extends ProductRequest {
  readonly initialLot?: { readonly quantity: number; readonly expiresOn: string };
}

export interface ProductQuery {
  readonly q: string;
  readonly categoryId: number | null;
  readonly state?: ProductStateFilter | null;
  readonly page: number;
  readonly size: number;
}
