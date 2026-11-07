import { TestBed } from '@angular/core/testing';
import { EvidenceComponent } from './ui';
describe('Evidencias', () => {
  it('distingue la cola pendiente de una imagen disponible', () => {
    const fixture = TestBed.createComponent(EvidenceComponent);
    fixture.componentRef.setInput('items', [{ id: 'e1', nombre: 'foto.png', url: '', fecha: '2026-01-01', status: 'PENDING' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('pendiente de subida');
    fixture.componentRef.setInput('items', [{ id: 'e1', nombre: 'foto.png', url: 'https://example.test/photo.png', fecha: '2026-01-01', status: 'UPLOADED' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('https://example.test/photo.png');
  });
});
