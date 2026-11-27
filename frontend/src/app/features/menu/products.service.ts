import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Page } from '../../shared/http/page';
import { Category, CreateProductRequest, Product, ProductQuery, ProductRequest } from './product.model';

export const PRODUCTS_URL = '/api/v1/products';
export const CATEGORIES_URL = '/api/v1/categories';

/** Acceso a la API de productos y categorías (LCV-HU-02). */
@Service()
export class ProductsService {
  readonly #http = inject(HttpClient);

  list(query: ProductQuery): Promise<Page<Product>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', 'name,asc');
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.categoryId !== null) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.state) {
      params = params.set('state', query.state);
    }
    return firstValueFrom(this.#http.get<Page<Product>>(PRODUCTS_URL, { params }));
  }

  categories(): Promise<Category[]> {
    return firstValueFrom(this.#http.get<Category[]>(CATEGORIES_URL));
  }

  create(body: CreateProductRequest): Promise<Product> {
    return firstValueFrom(this.#http.post<Product>(PRODUCTS_URL, body));
  }

  update(id: number, body: ProductRequest): Promise<Product> {
    return firstValueFrom(this.#http.put<Product>(`${PRODUCTS_URL}/${id}`, body));
  }

  setActive(id: number, active: boolean): Promise<unknown> {
    return firstValueFrom(this.#http.patch(`${PRODUCTS_URL}/${id}/active`, { active }));
  }
}
