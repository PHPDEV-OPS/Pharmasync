package com.example.pharmasync.ui.session

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

/** Base for tabs that share their host activity's [SessionViewModel]. */
abstract class SessionFragment : Fragment {
    constructor() : super()
    constructor(layoutId: Int) : super(layoutId)

    protected val session: SessionViewModel by activityViewModels {
        SessionViewModel.factory(requireActivity().application, (requireActivity() as SessionHost).sessionRole)
    }
}
