package com.tazzzo.app.data

import com.tazzzo.app.data.model.*

// Full quick-commerce taxonomy (Blinkit-style), Tazzzo branded.
object MockCatalog {

    val categories: List<Category> = listOf(
        // ----- Grocery & Kitchen -----
        Category("fruits", "Vegetables & Fruits", "🥦", 0xFFE8F5E9, "Grocery & Kitchen", listOf(
            Subcategory("fresh-veg", "Fresh Vegetables", "🥕"),
            Subcategory("fresh-fruit", "Fresh Fruits", "🍎"),
            Subcategory("exotics", "Exotics & Premium", "🥑"),
            Subcategory("leafy", "Leafy & Herbs", "🥬")
        )),
        Category("dairy", "Dairy, Bread & Eggs", "🥛", 0xFFFFF8E1, "Grocery & Kitchen", listOf(
            Subcategory("milk", "Milk", "🥛"),
            Subcategory("bread", "Bread & Pav", "🍞"),
            Subcategory("eggs", "Eggs", "🥚"),
            Subcategory("paneer", "Paneer & Curd", "🧀")
        )),
        Category("atta", "Atta, Rice & Dal", "🌾", 0xFFFFF3E0, "Grocery & Kitchen", listOf(
            Subcategory("atta-s", "Atta & Flours", "🌾"),
            Subcategory("rice-s", "Rice", "🍚"),
            Subcategory("dal-s", "Dal & Pulses", "🫘")
        )),
        Category("oil", "Oil, Masala & Dry Fruits", "🫒", 0xFFFFFDE7, "Grocery & Kitchen", listOf(
            Subcategory("oil-s", "Oils & Ghee", "🫒"),
            Subcategory("masala-s", "Masala & Spices", "🌶️"),
            Subcategory("dryfruit-s", "Dry Fruits & Seeds", "🥜")
        )),
        Category("meat", "Chicken, Meat & Fish", "🍗", 0xFFFFEBEE, "Grocery & Kitchen", listOf(
            Subcategory("chicken-s", "Chicken", "🍗"),
            Subcategory("fish-s", "Fish & Seafood", "🐟"),
            Subcategory("mutton-s", "Mutton", "🥩")
        )),
        // ----- Snacks & Drinks -----
        Category("munchies", "Munchies & Snacks", "🍿", 0xFFFFF3E0, "Snacks & Drinks", listOf(
            Subcategory("chips-s", "Chips & Crisps", "🍟"),
            Subcategory("namkeen-s", "Bhujia & Namkeen", "🥨"),
            Subcategory("popcorn-s", "Popcorn & Makhana", "🍿")
        )),
        Category("drinks", "Cold Drinks & Juices", "🥤", 0xFFE3F2FD, "Snacks & Drinks", listOf(
            Subcategory("soft-s", "Soft Drinks", "🥤"),
            Subcategory("juice-s", "Juices", "🧃"),
            Subcategory("water-s", "Water & Soda", "💧")
        )),
        Category("tea", "Tea, Coffee & More", "☕", 0xFFEFEBE9, "Snacks & Drinks", listOf(
            Subcategory("tea-s", "Tea", "🍵"),
            Subcategory("coffee-s", "Coffee", "☕"),
            Subcategory("health-s", "Health Drinks", "🥤")
        )),
        Category("instant", "Instant & Frozen Food", "🍜", 0xFFFCE4EC, "Snacks & Drinks", listOf(
            Subcategory("noodles-s", "Noodles & Pasta", "🍜"),
            Subcategory("frozen-s", "Frozen Veg & Snacks", "🧊"),
            Subcategory("readyeat-s", "Ready to Eat", "🍱")
        )),
        Category("sweet", "Sweet Tooth", "🍫", 0xFFF3E5F5, "Snacks & Drinks", listOf(
            Subcategory("choc-s", "Chocolates", "🍫"),
            Subcategory("icecream-s", "Ice Cream", "🍨"),
            Subcategory("indian-sweet-s", "Indian Sweets", "🍮")
        )),
        Category("bakery", "Bakery & Biscuits", "🍪", 0xFFFFF8E1, "Snacks & Drinks", listOf(
            Subcategory("biscuit-s", "Biscuits & Cookies", "🍪"),
            Subcategory("cakes-s", "Cakes & Muffins", "🧁"),
            Subcategory("rusk-s", "Rusk & Khari", "🥖")
        )),
        // ----- Beauty & Personal Care -----
        Category("personal", "Bath & Body", "🧼", 0xFFE0F7FA, "Beauty & Personal Care", listOf(
            Subcategory("soap-s", "Soaps & Body Wash", "🧼"),
            Subcategory("oral-s", "Oral Care", "🪥"),
            Subcategory("hair-s", "Hair Care", "💇")
        )),
        Category("skincare", "Skin & Face Care", "🧴", 0xFFFCE4EC, "Beauty & Personal Care", listOf(
            Subcategory("face-s", "Face Wash & Creams", "🧴"),
            Subcategory("grooming-s", "Men's Grooming", "🪒"),
            Subcategory("fragrance-s", "Deos & Fragrance", "🌸")
        )),
        Category("pharma", "Pharma & Wellness", "💊", 0xFFE8F5E9, "Beauty & Personal Care", listOf(
            Subcategory("firstaid-s", "First Aid", "🩹"),
            Subcategory("vitamins-s", "Vitamins", "💊"),
            Subcategory("protein-s", "Protein & Nutrition", "🥤")
        )),
        Category("baby", "Baby Care", "🍼", 0xFFE3F2FD, "Beauty & Personal Care", listOf(
            Subcategory("diaper-s", "Diapers & Wipes", "🧷"),
            Subcategory("babyfood-s", "Baby Food", "🍼")
        )),
        // ----- Household & Lifestyle -----
        Category("cleaning", "Cleaning Essentials", "🧹", 0xFFE8F5E9, "Household & Lifestyle", listOf(
            Subcategory("laundry-s", "Laundry", "🧺"),
            Subcategory("cleaners-s", "Floor & Surface", "🧴"),
            Subcategory("fresheners-s", "Fresheners & Repellents", "🌿")
        )),
        Category("home", "Home & Office", "🏠", 0xFFFFF3E0, "Household & Lifestyle", listOf(
            Subcategory("kitchenware-s", "Kitchenware", "🍳"),
            Subcategory("stationery-s", "Stationery", "✏️"),
            Subcategory("electricals-s", "Batteries & Bulbs", "🔋")
        )),
        Category("pet", "Pet Care", "🐾", 0xFFEFEBE9, "Household & Lifestyle", listOf(
            Subcategory("dogfood-s", "Dog Food", "🐶"),
            Subcategory("catfood-s", "Cat Food", "🐱")
        )),
        // Mirrors taxonomy v0.9.0 exactly: Household & Lifestyle >
        // Pooja & Religious Needs, sub-categories Daily Pooja and Pooja
        // Materials, verticals TZV-000225..TZV-000233. Names and shape are the
        // backend's, not ours, so the swap from mock to service is a data
        // change and not a re-modelling exercise.
        //
        // Deliberately NOT called "Festive". A festival is a merchandising
        // collection over these SKUs, which the taxonomy's own three-plane rule
        // puts outside taxonomy; ritual goods themselves sell all year.
        Category("pooja", "Pooja & Religious Needs", "🪔", 0xFFFFF3E0, "Household & Lifestyle", listOf(
            Subcategory("daily-pooja", "Daily Pooja", "🪔"),
            Subcategory("pooja-materials", "Pooja Materials", "🌸")
        )),
        Category("paan", "Paan Corner", "🍃", 0xFFE8F5E9, "Household & Lifestyle", listOf(
            Subcategory("mouthfresh-s", "Mouth Fresheners", "🍬"),
            Subcategory("smoking-s", "Candles & Lighters", "🕯️")
        ))
    )

    val products: List<Product> = listOf(
        // Vegetables & Fruits
        Product("p1", "Fresh Onion", "Tazzzo Farm", "🧅", "1 kg", 32, 40, "fruits", "fresh-veg", 0.0, 0, tags = listOf("Bestseller"), highlights = listOf("Farm fresh, sorted daily", "No cold storage")),
        Product("p2", "Fresh Tomato (Hybrid)", "Tazzzo Farm", "🍅", "500 g", 22, 30, "fruits", "fresh-veg", 0.0, 0, availability = Availability.LowStock(3), highlights = listOf("Rich red, ideal for gravies")),
        Product("p3", "Fresh Potato", "Tazzzo Farm", "🥔", "1 kg", 34, 42, "fruits", "fresh-veg", 0.0, 0, tags = listOf("Bestseller")),
        Product("p4", "Banana Robusta", "Tazzzo Farm", "🍌", "6 pcs", 42, 55, "fruits", "fresh-fruit", 0.0, 0, tags = listOf("Bestseller")),
        Product("p5", "Shimla Apple", "Tazzzo Farm", "🍎", "4 pcs (approx 500 g)", 119, 160, "fruits", "fresh-fruit", 0.0, 0),
        Product("p6", "Avocado Imported", "TazSelect", "🥑", "2 pcs", 189, 240, "fruits", "exotics", 0.0, 0, tags = listOf("Premium")),
        Product("p7", "Palak (Spinach)", "Tazzzo Farm", "🥬", "250 g", 18, 25, "fruits", "leafy", 0.0, 0),
        // Dairy
        Product("p8", "Toned Milk Pouch", "Amul", "🥛", "500 ml", 29, 30, "dairy", "milk", 0.0, 0, tags = listOf("Bestseller"), highlights = listOf("Pasteurised toned milk")),
        Product("p9", "Full Cream Milk", "Nandini", "🥛", "1 L", 56, 58, "dairy", "milk", 0.0, 0),
        Product("p10", "Brown Bread", "Modern", "🍞", "400 g", 45, 50, "dairy", "bread", 0.0, 0),
        Product("p11", "White Eggs", "Farm Made", "🥚", "6 pcs", 48, 60, "dairy", "eggs", 0.0, 0, tags = listOf("Bestseller")),
        Product("p12", "Malai Paneer", "Amul", "🧀", "200 g", 95, 105, "dairy", "paneer", 0.0, 0),
        Product("p13", "Greek Yogurt Blueberry", "Epigamia", "🫐", "90 g", 60, 70, "dairy", "paneer", 0.0, 0, availability = Availability.LowStock(2)),
        // Atta Rice Dal
        Product("p14", "Shudh Chakki Atta", "Aashirvaad", "🌾", "5 kg", 245, 285, "atta", "atta-s", 0.0, 0, tags = listOf("Bestseller"), highlights = listOf("100% whole wheat", "0% maida")),
        Product("p15", "Rozana Basmati Rice", "Daawat", "🍚", "1 kg", 145, 180, "atta", "rice-s", 0.0, 0, highlights = listOf("Daily-cook basmati")),
        Product("p16", "Toor Dal Unpolished", "Tata Sampann", "🫘", "1 kg", 165, 199, "atta", "dal-s", 0.0, 0),
        Product("p17", "Vacuum Iodised Salt", "Tata Salt", "🧂", "1 kg", 28, 30, "atta", "dal-s", 0.0, 0, tags = listOf("Bestseller")),
        // Oil & Masala
        Product("p18", "Sunlite Refined Sunflower Oil", "Fortune", "🌻", "1 L", 139, 165, "oil", "oil-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p19", "Pure Desi Ghee", "Amul", "🫕", "500 ml", 325, 360, "oil", "oil-s", 0.0, 0),
        Product("p20", "Kashmiri Red Chilli Powder", "Everest", "🌶️", "100 g", 78, 92, "oil", "masala-s", 0.0, 0),
        Product("p21", "California Almonds", "TazSelect", "🥜", "500 g", 389, 520, "oil", "dryfruit-s", 0.0, 0, tags = listOf("Premium")),
        // Meat
        Product("p22", "Chicken Curry Cut", "Fresh Basket", "🍗", "500 g", 159, 190, "meat", "chicken-s", 0.0, 0, highlights = listOf("Antibiotic-residue free", "Cut & cleaned")),
        Product("p23", "Rohu Fish Curry Cut", "Fresh Basket", "🐟", "500 g", 189, 220, "meat", "fish-s", 0.0, 0, availability = Availability.OutOfStock),
        // Munchies
        Product("p24", "Masala Munch", "Kurkure", "🍟", "90 g", 20, 20, "munchies", "chips-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p25", "Magic Masala Chips", "Lay's", "🍟", "73 g", 20, 20, "munchies", "chips-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p26", "Aloo Bhujia", "Haldiram's", "🥨", "400 g", 95, 110, "munchies", "namkeen-s", 0.0, 0),
        Product("p27", "Roasted Makhana", "Farmley", "🍿", "100 g", 149, 199, "munchies", "popcorn-s", 0.0, 0),
        // Drinks
        Product("p28", "Thums Up", "Coca-Cola", "🥤", "750 ml", 45, 50, "drinks", "soft-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p29", "Mixed Fruit Juice", "Real", "🧃", "1 L", 110, 130, "drinks", "juice-s", 0.0, 0),
        Product("p30", "Packaged Drinking Water", "Bisleri", "💧", "1 L", 20, 22, "drinks", "water-s", 0.0, 0),
        // Tea & Coffee
        Product("p31", "Gold Tea", "Tata Tea", "🍵", "500 g", 275, 310, "tea", "tea-s", 0.0, 0, highlights = listOf("Rich Assam blend")),
        Product("p32", "Instant Coffee Classic", "Nescafé", "☕", "100 g", 335, 380, "tea", "coffee-s", 0.0, 0),
        Product("p33", "Health Drink Chocolate", "Bournvita", "🥤", "500 g", 245, 270, "tea", "health-s", 0.0, 0),
        // Instant
        Product("p34", "2-Minute Masala Noodles", "Maggi", "🍜", "Pack of 4 (280 g)", 60, 64, "instant", "noodles-s", 0.0, 0, tags = listOf("Bestseller"), highlights = listOf("India's favourite 2-minute meal")),
        Product("p35", "Frozen Green Peas", "Safal", "🫛", "500 g", 89, 110, "instant", "frozen-s", 0.0, 0),
        Product("p36", "Ready Dal Makhani", "MTR", "🍱", "300 g", 99, 120, "instant", "readyeat-s", 0.0, 0),
        // Sweet
        Product("p37", "Dairy Milk Silk", "Cadbury", "🍫", "150 g", 175, 190, "sweet", "choc-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p38", "Butterscotch Tub", "Amul", "🍨", "1 L", 199, 250, "sweet", "icecream-s", 0.0, 0),
        Product("p39", "Gulab Jamun Tin", "Haldiram's", "🍮", "1 kg", 199, 230, "sweet", "indian-sweet-s", 0.0, 0),
        // Bakery
        Product("p40", "Dark Fantasy Choco Fills", "Sunfeast", "🍪", "300 g", 99, 130, "bakery", "biscuit-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p41", "Good Day Cashew", "Britannia", "🍪", "200 g", 35, 40, "bakery", "biscuit-s", 0.0, 0),
        Product("p42", "Choco Muffins", "TazBakes", "🧁", "2 pcs", 55, 70, "bakery", "cakes-s", 0.0, 0),
        // Personal Care
        Product("p43", "Lux Soft Glow Soap", "Lux", "🧼", "4 x 100 g", 99, 132, "personal", "soap-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p44", "Strong Teeth Toothpaste", "Colgate", "🪥", "200 g", 108, 125, "personal", "oral-s", 0.0, 0, highlights = listOf("Cavity protection")),
        Product("p45", "Anti-Hairfall Shampoo", "Dove", "💇", "340 ml", 315, 399, "personal", "hair-s", 0.0, 0),
        Product("p46", "Foaming Face Wash", "Himalaya", "🧴", "150 ml", 165, 190, "skincare", "face-s", 0.0, 0),
        Product("p47", "5-in-1 Trimmer", "Philips", "🪒", "1 unit", 1499, 1899, "skincare", "grooming-s", 0.0, 0, tags = listOf("Premium"), availability = Availability.OutOfStock),
        Product("p48", "Ice Cool Deo", "Fogg", "🌸", "150 ml", 199, 250, "skincare", "fragrance-s", 0.0, 0),
        // Pharma
        Product("p49", "Adhesive Bandages", "Band-Aid", "🩹", "20 strips", 45, 55, "pharma", "firstaid-s", 0.0, 0),
        Product("p50", "Vitamin C + Zinc Tablets", "Limcee", "💊", "15 tabs", 25, 30, "pharma", "vitamins-s", 0.0, 0),
        Product("p51", "Whey Protein Chocolate", "MuscleBlaze", "🥤", "1 kg", 2199, 2799, "pharma", "protein-s", 0.0, 0, tags = listOf("Premium")),
        // Baby
        Product("p52", "Baby Dry Pants M", "Pampers", "🧷", "38 pcs", 549, 699, "baby", "diaper-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p53", "Baby Cereal Wheat Apple", "Cerelac", "🍼", "300 g", 285, 310, "baby", "babyfood-s", 0.0, 0),
        // Cleaning
        Product("p54", "Matic Front Load Liquid", "Surf Excel", "🧺", "2 L", 385, 470, "cleaning", "laundry-s", 0.0, 0, tags = listOf("Bestseller")),
        Product("p55", "Disinfectant Floor Cleaner", "Lizol", "🧴", "975 ml", 199, 240, "cleaning", "cleaners-s", 0.0, 0),
        Product("p56", "Room Freshener Jasmine", "Odonil", "🌿", "220 ml", 99, 120, "cleaning", "fresheners-s", 0.0, 0),
        // Home
        Product("p57", "Non-Stick Fry Pan 24 cm", "Prestige", "🍳", "1 unit", 899, 1195, "home", "kitchenware-s", 0.0, 0),
        Product("p58", "Classmate Notebook", "ITC", "📓", "180 pages", 45, 50, "home", "stationery-s", 0.0, 0),
        Product("p59", "AA Batteries", "Duracell", "🔋", "4 pcs", 145, 180, "home", "electricals-s", 0.0, 0),
        // Pet
        Product("p60", "Adult Dog Food Chicken", "Pedigree", "🐶", "1.2 kg", 315, 360, "pet", "dogfood-s", 0.0, 0),
        Product("p61", "Cat Food Ocean Fish", "Whiskas", "🐱", "1.2 kg", 345, 399, "pet", "catfood-s", 0.0, 0),
        // Paan corner
        Product("p62", "Silver Coated Elaichi", "Pass Pass", "🍬", "85 g", 52, 60, "paan", "mouthfresh-s", 0.0, 0),
        Product("p63", "Scented Candles Pack", "HomeLite", "🕯️", "2 pcs", 149, 199, "paan", "smoking-s", 0.0, 0),
        // ----- Pooja & Religious Needs -----
        // One SKU per vertical in taxonomy v0.9.0 (TZV-000225..TZV-000233), so
        // the aisle can be validated against the real tree rather than a
        // shape we invented. Ritual goods sell year-round; a festival is a
        // collection over them, not a category of its own.
        Product("p64", "Sandal Agarbatti", "Cycle", "🧴", "72 sticks", 55, 65, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000225", tags = listOf("Bestseller")),
        Product("p65", "Sambrani Dhoop Sticks", "Cycle", "🌬️", "20 sticks", 65, 75, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000226"),
        Product("p66", "Pure Camphor Tablets", "Mangaldeep", "❄️", "50 g", 95, 110, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000227"),
        Product("p67", "Cotton Diya Wicks (Long)", "Shubhkart", "🧵", "100 pcs", 39, 45, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000228"),
        Product("p68", "Mitti Diya", "Tazzzo Home", "🪔", "12 pcs", 59, 75, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000229", tags = listOf("Bestseller")),
        Product("p69", "Deepam Pooja Oil (Non-Edible)", "VVV", "🛢️", "500 ml", 149, 165, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000230", highlights = listOf("Not for cooking")),
        Product("p70", "Havan Samagri", "Shubhkart", "🔥", "200 g", 89, 105, "pooja", "daily-pooja", 0.0, 0, verticalId = "TZV-000231"),
        Product("p71", "Roli Chandan Kumkum Set", "Shubhkart", "🔴", "4 pcs", 59, 69, "pooja", "pooja-materials", 0.0, 0, verticalId = "TZV-000232"),
        Product("p72", "Brass Pooja Thali Set", "Tazzzo Home", "🟡", "1 set", 449, 599, "pooja", "pooja-materials", 0.0, 0, verticalId = "TZV-000233", tags = listOf("Premium"))
    )

    val banners = listOf(
        PromoBanner("b1", "SAVE 8–20%", "on market prices, every day", "🛒", dark = false),
        PromoBanner("b2", "Fast Delivery", "freshly sourced, doorstep fast", "⚡", dark = true),
        PromoBanner("b3", "Earn Tazzzo Coins", "rewards on every order", "🪙", dark = false)
    )

    val faqs = listOf(
        FaqItem("Where is my order?", "Track your order any time from Account → Your Orders."),
        FaqItem("How do Tazzzo Coins work?", "You earn Tazzzo Coins on every order, redeemable against your next one. Current rates are shown on the Tazzzo Coins screen."),
        FaqItem("What is the delivery fee?", "Delivery is free above a minimum order value; the exact fee is always shown in your cart before you pay."),
        FaqItem("How do I return an item?", "Raise a return from Your Orders within 24 hours for fresh items and 7 days for packaged goods."),
        FaqItem("What is voice ordering?", "India's first voice commerce — speak your list, we build your cart. Coming soon!"),
        FaqItem("Can I order on WhatsApp?", "Yes! Say \"HI\" to 8050316087 on WhatsApp and order right from chat.")
    )

    fun productsFor(categoryId: String, subcategoryId: String? = null) =
        products.filter { it.categoryId == categoryId && (subcategoryId == null || it.subcategoryId == subcategoryId) }

    fun bestsellers() = products.filter { "Bestseller" in it.tags }

    /**
     * Every SKU whose MRP genuinely exceeds its selling price, deepest saving
     * first, measured in rupees rather than percent.
     *
     * Ranked by rupees because that is the figure a customer can verify against
     * the pack, and because a 40% saving on a ₹10 item is not a better deal
     * than ₹300 off a ₹2,000 one. Nothing is promoted into this list by a flag.
     */
    fun deals() = products
        .filter { it.mrp > it.price }
        .sortedByDescending { it.mrp - it.price }

    fun search(query: String): List<Product> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return products.filter {
            it.name.lowercase().contains(q) || it.brand.lowercase().contains(q) ||
                categories.find { c -> c.id == it.categoryId }?.name?.lowercase()?.contains(q) == true
        }
    }
}
