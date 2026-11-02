package com.flipcash.app.messenger.internal.request

import com.flipcash.features.messenger.R
import com.getcode.util.resources.ResourceHelper

// The New Request summary's wording, out of Compose so the spec's cases can pin it.

/** The People row's value. */
internal fun peopleValue(people: NewRequestDraft.People, resources: ResourceHelper): String = when (people) {
    NewRequestDraft.People.Choose -> resources.getString(R.string.value_requestPeople_choose)
    NewRequestDraft.People.EveryoneIncludingYou -> resources.getString(R.string.value_requestPeople_everyoneIncludingYou)
    NewRequestDraft.People.EveryoneExceptYou -> resources.getString(R.string.value_requestPeople_everyoneExceptYou)
    is NewRequestDraft.People.SomeIncludingYou ->
        resources.getQuantityString(R.plurals.value_requestPeople_someIncludingYou, people.others, people.others)
    is NewRequestDraft.People.Some ->
        resources.getQuantityString(R.plurals.value_requestPeople_some, people.count, people.count)
}

/** The People row's second line: only when the creator is included. */
internal fun peopleDetail(people: NewRequestDraft.People, resources: ResourceHelper): String? =
    resources.getString(R.string.subtitle_requestMarkedPaid).takeIf { people.markedPaid }

/**
 * The summary's button, "Request <share> Each", [share] being the smallest share formatted. When
 * [uneven], some shares are a unit more, so it reads "Request ~<share> Each".
 */
internal fun requestButtonText(share: String, uneven: Boolean, resources: ResourceHelper): String =
    resources.getString(if (uneven) R.string.action_requestEachApprox else R.string.action_requestEach, share)

/** The sheet for checking a 101st share: title and body. */
internal fun maxPeopleNotice(resources: ResourceHelper): Pair<String, String> =
    resources.getString(R.string.title_requestMaxPeople) to resources.getString(R.string.description_requestMaxPeople)
