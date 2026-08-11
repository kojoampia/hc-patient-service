/**
 * Mongock change units and profile-gated data initializers.
 *
 * <p>The package Mongock scans is set by {@code mongock.migration-scan-package} in {@code application.yml}. A change
 * unit runs once, in every profile; anything that must be restricted to development belongs in an
 * {@code ApplicationRunner} here instead — see {@link net.jojoaddison.config.dbmigrations.DemoDataInitializer}.</p>
 */
package net.jojoaddison.config.dbmigrations;
