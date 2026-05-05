// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.data

/** Opt-in marker for APIs that expose internal storage or representation invariants. */
@Retention(AnnotationRetention.RUNTIME)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API exposes internal representation details and invariants.",
)
annotation class InternalDataApi
