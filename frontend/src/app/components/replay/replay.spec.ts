import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ReplayComponent } from './replay';

describe('ReplayComponent', () => {
  async function create() {
    await TestBed.configureTestingModule({
      imports: [ReplayComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(ReplayComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('should create with zero backend dependencies', async () => {
    // If ReplayComponent injected HttpClient or ApiService this would throw
    // without the corresponding providers in TestBed.
    const fixture = await create();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should offer four scripted scenarios', async () => {
    const fixture = await create();
    const buttons = fixture.nativeElement.querySelectorAll('.scenario-btn');
    expect(buttons.length).toBe(4);
    const names = Array.from(buttons).map((b) => (b as HTMLElement).textContent);
    expect(names.join(' ')).toContain('GPS spoofing event');
    expect(names.join(' ')).toContain('Altitude deviation');
    expect(names.join(' ')).toContain('Unusual holding pattern');
    expect(names.join(' ')).toContain('Speed excursion');
  });

  it('should switch scenarios on selection and reset the clock', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    comp.selectScenario('holding');
    fixture.detectChanges();

    expect(comp.activeId()).toBe('holding');
    expect(comp.clock()).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Unusual holding pattern');
  });

  it('should advance the clock while playing and stop at the end', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    vi.useFakeTimers();
    try {
      expect(comp.playing()).toBe(false);
      comp.togglePlay();
      vi.advanceTimersByTime(1000);
      fixture.detectChanges();
      expect(comp.playing()).toBe(true);
      expect(comp.clock()).toBeGreaterThan(0);

      // Advance well past the end of the scenario.
      vi.advanceTimersByTime(comp.duration() * 1000 * 2);
      fixture.detectChanges();
      expect(comp.clock()).toBe(comp.duration());
      expect(comp.playing()).toBe(false);
    } finally {
      comp.reset();
      vi.useRealTimers();
    }
  });

  it('should reset the replay to T+0', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    vi.useFakeTimers();
    try {
      comp.togglePlay();
      vi.advanceTimersByTime(2000);
      fixture.detectChanges();
      expect(comp.clock()).toBeGreaterThan(0);

      comp.reset();
      fixture.detectChanges();
      expect(comp.clock()).toBe(0);
      expect(comp.playing()).toBe(false);
      expect(comp.log().length).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('should log anomalies deterministically as the clock advances', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    expect(comp.log().length).toBe(0);

    // GPS spoofing scenario flags at T+56s (i=14) and T+96s (i=24).
    comp.scrub({ target: { value: '60' } } as unknown as Event);
    fixture.detectChanges();
    expect(comp.log().length).toBe(1);
    expect(comp.log()[0].explanation).toContain('spoofing');

    // Scrubbing back deterministically clears later flags.
    comp.scrub({ target: { value: '0' } } as unknown as Event);
    fixture.detectChanges();
    expect(comp.log().length).toBe(0);

    // Same scrub position always yields the same log.
    comp.scrub({ target: { value: '140' } } as unknown as Event);
    fixture.detectChanges();
    const first = comp.log().map((e) => e.t).join(',');
    comp.scrub({ target: { value: '0' } } as unknown as Event);
    comp.scrub({ target: { value: '140' } } as unknown as Event);
    fixture.detectChanges();
    expect(comp.log().map((e) => e.t).join(',')).toBe(first);
    expect(comp.log().length).toBe(2);
  });

  it('should render detection log entries with source badges', async () => {
    const fixture = await create();
    const comp = fixture.componentInstance;

    comp.scrub({ target: { value: '100' } } as unknown as Event);
    fixture.detectChanges();

    const entries = fixture.nativeElement.querySelectorAll('.event');
    expect(entries.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('ESCALATED');
    expect(fixture.nativeElement.textContent).toContain('RULE');
    expect(fixture.nativeElement.textContent).toContain('thread #1042');
  });
});
