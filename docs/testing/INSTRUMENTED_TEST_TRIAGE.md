# Instrumented test triage (App hardening PR-2)

Eleven legacy instrumented tests failed on main after the UI-02 Home redesign. Cause for all: they drove the MOCK Home of the
time (aisle tiles on Home, a "Categories" tab, an "Account" tab). Since UI-02 the aisle tiles live on the Shop tab, the tabs are
Home / Shop / Deals / Orders / Order Again / Profile, and Home is the editorial landing page. No production navigation was
changed to satisfy these tests. None found an app defect.

Classes: A = stale because UI/navigation intentionally changed. C = obsolete, tests a MOCK path no longer supported.

| Test | Class | Action |
|---|---|---|
| NavigationJourneyTest: shop_to_category_to_pdp_and_back (was home_to_category...) | A | Enters through the Shop tab; back lands on Shop |
| NavigationJourneyTest: rapid_taps_do_not_stack_duplicate_destinations | A | Enters through Shop |
| NavigationJourneyTest: category_scroll_position_survives... | A | Enters through Shop |
| NavigationJourneyTest: tab_switching_preserves_each_tabs_state | A | Shop/Home tab labels; Shop state asserted on return |
| InteractionSemanticsTest: bottom_nav_publishes_selected_state | A | Current tab set |
| InteractionSemanticsTest: switching_tab_moves_the_selected_state | A | Categories -> Shop |
| CheckoutJourneyTest: chosen_slot_and_fee_follow_the_order | A | Precondition no longer waits for aisle tiles on Home |
| DealsTabTest: deals_is_a_destination | A | Same precondition fix |
| RedesignEvidenceTest: categories_account_and_help | A | Categories -> Shop, Account -> Profile |
| HomeEvidenceTest: home_is_a_commerce_destination... | C | Retired: asserted the old MOCK Home (festival hero, aisle grid on Home). The shipped Home is covered by HomeUi02EvidenceTest |
| CartPromotionJourneyTest: member_basket_shows_itemised_offers... | C | Retired: MOCK-only Tazzzo Club purchase and coupon journey, unreachable in REMOTE. The arithmetic is covered by PromotionEngineTest and MembershipTest |

None was disabled with @Ignore.
