package com.tazzzo.app.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The single icon vocabulary for Tazzzo.
 *
 * Rule: emoji are CONTENT (a product's 🍅, a category tile), never UI. Every
 * control — navigation, search, cart, steppers, chevrons, status — draws from
 * this one family so the app reads as a single designed product, tints with
 * the theme, scales with the user's font size and carries proper semantics.
 */
object TazIcons {
    // navigation
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Filled.ArrowForwardIos
    val Chevron: ImageVector = Icons.AutoMirrored.Filled.ArrowForwardIos
    val Dropdown: ImageVector = Icons.Default.KeyboardArrowDown
    val Expand: ImageVector = Icons.Default.ExpandMore
    val Close: ImageVector = Icons.Default.Close

    // bottom nav
    val Home: ImageVector = Icons.Default.Home
    val Categories: ImageVector = Icons.Default.Apps
    /** Primary tabs (Home.jpeg): a rounded house, a 2x2 grid, a receipt, a person. */
    val Shop: ImageVector = Icons.Outlined.GridView
    val Orders: ImageVector = Icons.AutoMirrored.Outlined.ReceiptLong
    val Profile: ImageVector = Icons.Outlined.PersonOutline
    val HomeOutlined: ImageVector = Icons.Outlined.Home
    val OrderAgain: ImageVector = Icons.Default.Replay
    val Account: ImageVector = Icons.Default.Person

    // commerce
    val Search: ImageVector = Icons.Default.Search
    val Cart: ImageVector = Icons.Default.ShoppingCart
    val Bag: ImageVector = Icons.Default.ShoppingBag
    val Plus: ImageVector = Icons.Default.Add
    val Minus: ImageVector = Icons.Default.Remove
    val Offer: ImageVector = Icons.Outlined.LocalOffer
    /** Expand / collapse affordance for disclosure rows (savings breakdown, fee reasons). */
    val ChevronDown: ImageVector = Icons.Default.KeyboardArrowDown
    val ChevronUp: ImageVector = Icons.Default.KeyboardArrowUp
    val Store: ImageVector = Icons.Default.Storefront
    val Inventory: ImageVector = Icons.Outlined.Inventory2
    val Filter: ImageVector = Icons.Default.Tune

    // fulfilment
    val Location: ImageVector = Icons.Default.LocationOn
    val Delivery: ImageVector = Icons.Default.Bolt
    val Slot: ImageVector = Icons.Default.Schedule
    val Payment: ImageVector = Icons.Default.Payments
    val Card: ImageVector = Icons.Default.CreditCard
    val Receipt: ImageVector = Icons.Default.Receipt

    // brand / features
    val Mic: ImageVector = Icons.Default.Mic
    /** Two stacked coins — currency-neutral. A '$' glyph would be wrong for a
     *  rupee product; Tazzzo Coins are a loyalty token, not dollars. */
    val Coin: ImageVector = Icons.Default.Toll
    val Star: ImageVector = Icons.Default.Star

    // status
    val Success: ImageVector = Icons.Default.CheckCircle
    val Check: ImageVector = Icons.Default.Check
    val Bell: ImageVector = Icons.Outlined.Notifications
    val Globe: ImageVector = Icons.Outlined.Language
    val Edit: ImageVector = Icons.Outlined.Edit

    val Error: ImageVector = Icons.Default.ErrorOutline
    val Offline: ImageVector = Icons.Default.WifiOff
    val Info: ImageVector = Icons.Default.Info
    val Retry: ImageVector = Icons.Default.Refresh
    val Delete: ImageVector = Icons.Default.Delete

    // account
    val Help: ImageVector = Icons.AutoMirrored.Filled.HelpOutline
    val Chat: ImageVector = Icons.AutoMirrored.Filled.Chat
    val Logout: ImageVector = Icons.AutoMirrored.Filled.Logout
}
