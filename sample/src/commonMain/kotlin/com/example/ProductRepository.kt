package com.example

import io.swiftify.annotations.SwiftDefaults
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

// Multiplatform time helper
private val productTimeSource = TimeSource.Monotonic
private val productStartMark = productTimeSource.markNow()

private fun currentTimeMillis(): Long = productStartMark.elapsedNow().inWholeMilliseconds

/**
 * Real-world example: E-commerce Product Repository
 */
class ProductRepository {
    private val _cart = MutableStateFlow(Cart(items = emptyList(), total = 0.0))

    /**
     * Shopping cart state - observe for real-time updates
     */
    @SwiftFlow
    val cart: StateFlow<Cart> = _cart

    // MARK: - Product Fetching

    /**
     * Fetch products with pagination - common REST API pattern
     */
    @SwiftDefaults
    suspend fun getProducts(
        page: Int = 1,
        pageSize: Int = 20,
        category: String? = null,
    ): ProductPage {
        delay(300) // Simulate network
        val products =
            (1..pageSize).map { index ->
                val id = (page - 1) * pageSize + index
                Product(
                    id = "prod_$id",
                    name = "Product $id",
                    price = (10.0 + id * 2.5),
                    category = category ?: "General",
                    inStock = id % 3 != 0,
                )
            }
        return ProductPage(
            products = products,
            currentPage = page,
            totalPages = 10,
            hasMore = page < 10,
        )
    }

    /**
     * Search products - with optional filters
     */
    @SwiftDefaults
    suspend fun searchProducts(
        query: String,
        minPrice: Double = 0.0,
        maxPrice: Double = 1000.0,
        inStockOnly: Boolean = false,
    ): List<Product> {
        delay(200)
        return listOf(
            Product("search_1", "Found: $query Item 1", 29.99, "Search", true),
            Product("search_2", "Found: $query Item 2", 49.99, "Search", inStockOnly),
            Product("search_3", "Found: $query Item 3", 19.99, "Search", true),
        ).filter { it.price in minPrice..maxPrice }
    }

    /**
     * Get product details by ID
     */
    @SwiftDefaults
    suspend fun getProductDetails(productId: String): ProductDetails {
        delay(150)
        return ProductDetails(
            product = Product(productId, "Product $productId", 99.99, "Electronics", true),
            description = "This is a detailed description for $productId",
            images = listOf("image1.jpg", "image2.jpg"),
            reviews =
            listOf(
                Review("user1", 5, "Great product!"),
                Review("user2", 4, "Good value"),
            ),
            relatedProducts = listOf("prod_101", "prod_102"),
        )
    }

    // MARK: - Cart Operations

    /**
     * Add item to cart
     */
    @SwiftDefaults
    suspend fun addToCart(
        productId: String,
        quantity: Int = 1,
    ): Cart {
        delay(100)
        val currentCart = _cart.value
        val existingItem = currentCart.items.find { it.productId == productId }

        val newItems =
            if (existingItem != null) {
                currentCart.items.map {
                    if (it.productId == productId) {
                        it.copy(quantity = it.quantity + quantity)
                    } else {
                        it
                    }
                }
            } else {
                currentCart.items + CartItem(productId, "Product $productId", 29.99, quantity)
            }

        val newCart = Cart(items = newItems, total = newItems.sumOf { it.price * it.quantity })
        _cart.value = newCart
        return newCart
    }

    /**
     * Remove item from cart
     */
    @SwiftDefaults
    suspend fun removeFromCart(productId: String): Cart {
        delay(100)
        val newItems = _cart.value.items.filter { it.productId != productId }
        val newCart = Cart(items = newItems, total = newItems.sumOf { it.price * it.quantity })
        _cart.value = newCart
        return newCart
    }

    /**
     * Checkout - returns order result
     */
    @SwiftDefaults
    suspend fun checkout(paymentMethod: String): OrderResult {
        delay(500) // Simulate payment processing
        val cart = _cart.value
        if (cart.items.isEmpty()) {
            return OrderResult.Failed("Cart is empty")
        }

        // Clear cart after successful checkout
        _cart.value = Cart(items = emptyList(), total = 0.0)

        return OrderResult.Success(
            orderId = "order_${currentTimeMillis()}",
            total = cart.total,
            estimatedDelivery = "3-5 business days",
        )
    }

    // MARK: - Real-time Features

    /**
     * Watch for price changes on a product - useful for deal alerts
     */
    @SwiftFlow
    fun watchPriceChanges(productId: String): Flow<PriceUpdate> = flow {
        var currentPrice = 99.99
        repeat(10) { i ->
            delay(2000) // Price update every 2 seconds
            val change = if (i % 2 == 0) -5.0 else 3.0
            currentPrice += change
            emit(
                PriceUpdate(
                    productId = productId,
                    oldPrice = currentPrice - change,
                    newPrice = currentPrice,
                    timestamp = currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Live inventory updates - useful for showing stock availability
     */
    @SwiftFlow
    fun watchInventory(productIds: List<String>): Flow<InventoryUpdate> = flow {
        repeat(5) { i ->
            delay(1500)
            productIds.forEach { productId ->
                emit(
                    InventoryUpdate(
                        productId = productId,
                        inStock = i % 2 == 0,
                        quantity = (10 - i * 2).coerceAtLeast(0),
                    ),
                )
            }
        }
    }
}

// MARK: - Data Models

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val inStock: Boolean,
)

data class ProductPage(
    val products: List<Product>,
    val currentPage: Int,
    val totalPages: Int,
    val hasMore: Boolean,
)

data class ProductDetails(
    val product: Product,
    val description: String,
    val images: List<String>,
    val reviews: List<Review>,
    val relatedProducts: List<String>,
)

data class Review(
    val userId: String,
    val rating: Int,
    val comment: String,
)

data class Cart(
    val items: List<CartItem>,
    val total: Double,
)

data class CartItem(
    val productId: String,
    val name: String,
    val price: Double,
    val quantity: Int,
)

data class PriceUpdate(
    val productId: String,
    val oldPrice: Double,
    val newPrice: Double,
    val timestamp: Long,
)

data class InventoryUpdate(
    val productId: String,
    val inStock: Boolean,
    val quantity: Int,
)

sealed class OrderResult {
    data class Success(
        val orderId: String,
        val total: Double,
        val estimatedDelivery: String,
    ) : OrderResult()

    data class Failed(
        val reason: String,
    ) : OrderResult()
}
