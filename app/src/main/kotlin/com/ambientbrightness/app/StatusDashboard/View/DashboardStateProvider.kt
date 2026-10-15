package com.ambientbrightness.app.StatusDashboard.View

import kotlinx.coroutines.flow.Flow

/**
 * Supplies the dashboard with one combined read of every service value it renders.
 *
 * It is the presentation-side seam between the collector (which knows the five service boundaries)
 * and the ViewModel (which only maps and publishes), so the ViewModel can be tested with a plain
 * flow of [DashboardSnapshot]s and the collector can be tested on its own.
 */
interface DashboardStateProvider {

    /** A cold flow of snapshots; it emits whenever any underlying service value changes. */
    fun snapshots(): Flow<DashboardSnapshot>
}
