
const context = window.location.pathname.substring(
    0,
    window.location.pathname.indexOf("/", 1)
);

const api = context + "/api";

let products = [];

async function request(url, options = {}) {
    const response = await fetch(url, options);

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Request failed");
    }

    return response.json();
}

async function loadProducts() {
    const message = document.getElementById("productMessage");

    try {
        products = await request(api + "/products");
        renderProducts();
        await loadCart();
    } catch (error) {
        message.textContent =
            "Could not load products. Check the server and database.";
    }
}

function renderProducts() {
    const grid = document.getElementById("productGrid");
    const search = document.getElementById("searchInput")
        .value.toLowerCase().trim();

    const category =
        document.getElementById("categoryFilter").value;

    const filtered = products.filter(product => {
        const matchesSearch =
            product.name.toLowerCase().includes(search) ||
            (product.description || "").toLowerCase().includes(search);

        const matchesCategory =
            category === "all" || product.category === category;

        return matchesSearch && matchesCategory;
    });

    grid.replaceChildren();

    if (filtered.length === 0) {
        grid.textContent = "No matching products found.";
        return;
    }

    filtered.forEach(product => {
        const card = document.createElement("article");
        card.className = "product-card";

        const image = document.createElement("img");
        image.src = product.image || "https://placehold.co/400x300";
        image.alt = product.name;
        image.loading = "lazy";

        image.onerror = () => {
            image.onerror = null;
            image.src = "https://placehold.co/400x300?text=Product";
        };

        const title = document.createElement("h3");
        title.textContent = product.name;

        const description = document.createElement("p");
        description.textContent = product.description || "";

        const price = document.createElement("p");
        price.className = "price";
        price.textContent = "₹" +
            Number(product.price).toFixed(2);

        const stock = document.createElement("p");
        stock.textContent = product.stock > 0
            ? "In stock: " + product.stock
            : "Out of stock";

        const button = document.createElement("button");
        button.className = "primary-btn";
        button.textContent = "Add to Cart";
        button.disabled = product.stock < 1;

        button.addEventListener("click", async () => {
            try {
                await request(api + "/cart", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        id: product.id,
                        quantity: 1
                    })
                });

                await loadCart();
                document.getElementById("productMessage")
                    .textContent = "Product added to cart!";
            } catch (error) {
                document.getElementById("productMessage")
                    .textContent = "Could not add product. " + error.message;
            }
        });

        card.append(image, title, description, price, stock, button);
        grid.appendChild(card);
    });
}

async function loadCart() {
    const cart = await request(api + "/cart");
    const container = document.getElementById("cartItems");

    container.replaceChildren();

    let count = 0;

    cart.items.forEach(item => {
        count += item.quantity;

        const row = document.createElement("div");
        row.className = "cart-item";

        const info = document.createElement("span");
        info.textContent =
            `${item.name} × ${item.quantity} — ₹${Number(item.subtotal).toFixed(2)}`;

        const remove = document.createElement("button");
        remove.textContent = "Remove";

        remove.addEventListener("click", async () => {
            try {
                await request(
                    api + "/cart?id=" + encodeURIComponent(item.id),
                    { method: "DELETE" }
                );
                await loadCart();
            } catch (error) {
                alert("Could not remove item.");
            }
        });

        row.append(info, remove);
        container.appendChild(row);
    });

    if (cart.items.length === 0) {
        container.textContent = "Your cart is empty.";
    }

    document.getElementById("cartCount").textContent = count;
    document.getElementById("cartTotal").textContent =
        Number(cart.total).toFixed(2);
}

document.getElementById("searchInput")
    .addEventListener("input", renderProducts);

document.getElementById("categoryFilter")
    .addEventListener("change", renderProducts);

document.getElementById("checkoutForm")
    .addEventListener("submit", async event => {
        event.preventDefault();

        const address = document.getElementById("address").value.trim();
        const message = document.getElementById("checkoutMessage");

        if (!address) {
            message.textContent = "Please enter a shipping address.";
            return;
        }

        try {
            const result = await request(api + "/checkout", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ address })
            });

            message.textContent =
                `Order #${result.orderId} placed successfully. ` +
                `Total: ₹${Number(result.total).toFixed(2)}`;

            document.getElementById("address").value = "";

            await loadCart();
        } catch (error) {
            message.textContent =
                "Checkout failed. Please check your cart and try again.";
        }
    });

loadProducts();
