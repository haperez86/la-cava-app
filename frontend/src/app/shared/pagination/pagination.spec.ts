import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DEFAULT_PAGE_SIZE, Pagination, pageWindow } from './pagination';

/** componente de paginación compartido. */
describe('Pagination', () => {
  let fixture: ComponentFixture<Pagination>;
  let root: HTMLElement;

  function render(page: number, size: number, totalElements: number): void {
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('size', size);
    fixture.componentRef.setInput('totalElements', totalElements);
    fixture.detectChanges();
  }

  function button(testId: string): HTMLButtonElement {
    return root.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Pagination] });
    fixture = TestBed.createComponent(Pagination);
    root = fixture.nativeElement as HTMLElement;
  });

  it('defines the default page size as 10 (CA2)', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(10);
  });

  it('shows the range, the page numbers and disables the extremes (CA1)', () => {
    render(0, 10, 35);

    expect(root.querySelector('[data-testid="pagination-summary"]')?.textContent).toContain(
      'Mostrando 1–10 de 35',
    );
    expect(button('pagination-prev').disabled).toBe(true);
    expect(button('pagination-next').disabled).toBe(false);
    expect(root.querySelectorAll('.page').length).toBe(4);
    expect(root.querySelector('.page.active')?.textContent?.trim()).toBe('1');
    expect(root.querySelector('[data-testid="pagination-mobile"]')?.textContent).toContain(
      'Página 1 de 4',
    );
  });

  it('emits the requested page from the arrows and the page numbers (CA1)', () => {
    render(1, 10, 35);
    const pages: number[] = [];
    fixture.componentInstance.pageChange.subscribe((page) => pages.push(page));

    button('pagination-next').click();
    button('pagination-prev').click();
    Array.from(root.querySelectorAll<HTMLButtonElement>('.page'))
      .find((el) => el.textContent?.trim() === '4')!
      .click();

    expect(pages).toEqual([2, 0, 3]);
  });

  it('disables next on the last page', () => {
    render(3, 10, 35);

    expect(root.querySelector('[data-testid="pagination-summary"]')?.textContent).toContain(
      'Mostrando 31–35 de 35',
    );
    expect(button('pagination-next').disabled).toBe(true);
    expect(button('pagination-prev').disabled).toBe(false);
  });

  it('uses an ellipsis window with many pages (CA1)', () => {
    render(9, 10, 200);

    const items = Array.from(root.querySelectorAll('.pages > *')).map((el) => el.textContent?.trim());
    expect(items).toEqual(['1', '…', '9', '10', '11', '…', '20']);
    expect(root.querySelector('[data-testid="pagination-mobile"]')?.textContent).toContain(
      'Página 10 de 20',
    );
  });

  it('shows an empty state without controls', () => {
    render(0, 10, 0);

    expect(root.querySelector('[data-testid="pagination-summary"]')?.textContent).toContain(
      'Mostrando 0 de 0',
    );
    expect(root.querySelector('.controls')).toBeNull();
  });
});

describe('pageWindow', () => {
  it('returns every page when there are few and a window with separators when there are many', () => {
    expect(pageWindow(0, 5)).toEqual([0, 1, 2, 3, 4]);
    expect(pageWindow(5, 20)).toEqual([0, 'ellipsis', 4, 5, 6, 'ellipsis', 19]);
    expect(pageWindow(0, 20)).toEqual([0, 1, 2, 3, 'ellipsis', 19]);
    expect(pageWindow(19, 20)).toEqual([0, 'ellipsis', 16, 17, 18, 19]);
  });
});
