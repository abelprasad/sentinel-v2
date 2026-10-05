import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AnomalyDetailComponent } from './anomaly-detail';
import { AnomalyDto } from '../../core/models/api.models';

function makeAnomaly(overrides: Partial<AnomalyDto> = {}): AnomalyDto {
  return {
    id: 1,
    icaoHex: 'A1B2C3',
    callsign: 'N123AB',
    score: 8.5,
    zAltitude: 0.4,
    zSpeed: 0.9,
    zHeading: 0.2,
    zPosition: 9.4,
    explanation: 'Position z-score 9.4 exceeds the spoofing threshold.',
    explanationSrc: 'rule',
    parentAnomalyId: null,
    acknowledged: false,
    escalated: true,
    flaggedAt: '2026-10-05T17:00:00Z',
    ...overrides,
  };
}

describe('AnomalyDetailComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnomalyDetailComponent],
    }).compileComponents();
  });

  async function create(anomaly: AnomalyDto) {
    const fixture = TestBed.createComponent(AnomalyDetailComponent);
    fixture.componentRef.setInput('anomaly', anomaly);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('should create', async () => {
    const fixture = await create(makeAnomaly());
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render four z-score bars, one per dimension', async () => {
    const fixture = await create(makeAnomaly());
    const rows = fixture.nativeElement.querySelectorAll('.zrow');
    expect(rows.length).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('Altitude');
    expect(fixture.nativeElement.textContent).toContain('Position');
  });

  it('should display the explanation text prominently', async () => {
    const fixture = await create(makeAnomaly());
    const text = fixture.nativeElement.querySelector('.expl-text')?.textContent ?? '';
    expect(text).toContain('spoofing threshold');
  });

  it('should show RULE badge for rule explanations and AI for llm', async () => {
    const rule = await create(makeAnomaly({ explanationSrc: 'rule' }));
    expect(rule.nativeElement.querySelector('.src-badge')?.textContent).toContain('RULE');

    const llm = await create(makeAnomaly({ explanationSrc: 'llm' }));
    expect(llm.nativeElement.querySelector('.src-badge')?.textContent).toContain('AI');
  });

  it('should show incident thread link when parentAnomalyId is set', async () => {
    const fixture = await create(makeAnomaly({ parentAnomalyId: 1042 }));
    expect(fixture.nativeElement.textContent).toContain('Part of incident thread');
    expect(fixture.nativeElement.textContent).toContain('#1042');
  });

  it('should not show thread link without parentAnomalyId', async () => {
    const fixture = await create(makeAnomaly({ parentAnomalyId: null }));
    expect(fixture.nativeElement.querySelector('.thread')).toBeNull();
  });

  it('should handle null z-scores gracefully', async () => {
    const fixture = await create(
      makeAnomaly({ zAltitude: null, zSpeed: null, zHeading: null, zPosition: null }),
    );
    expect(fixture.nativeElement.textContent).toContain('n/a');
  });
});
