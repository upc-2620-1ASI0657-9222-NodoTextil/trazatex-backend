package com.nodotextil.trazatex.organizationaccess.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clocks for tests. */
public final class FixedClocks {

	private FixedClocks() {
	}

	public static Clock at(LocalDateTime utc) {
		return Clock.fixed(utc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
	}

	/** A clock the test can move forward. */
	public static final class Mutable extends Clock {

		private Instant instant;

		public Mutable(LocalDateTime start) {
			this.instant = start.toInstant(ZoneOffset.UTC);
		}

		public void advanceMinutes(long minutes) {
			instant = instant.plusSeconds(minutes * 60);
		}

		public void advanceDays(long days) {
			instant = instant.plusSeconds(days * 86_400);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
