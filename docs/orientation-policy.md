# Orientation policy

LUMA is portrait-only. `MainActivity` declares portrait orientation, and runtime verification attempts rotation to confirm the activity remains portrait. This supports the calm, capture-first phone layout and avoids untested landscape arrangements.

Adaptive handling is still required within portrait: compact widths use available space, medium portrait widths cap content at 720 dp, and expanded portrait widths cap it at 840 dp. This preserves readable line lengths without reclassifying the product as a landscape or tablet dashboard.
