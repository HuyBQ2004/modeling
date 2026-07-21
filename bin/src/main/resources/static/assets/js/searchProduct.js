document.getElementById("search-input").addEventListener("input", function () {
    const searchQuery = document.getElementById("search-input").value;
    if (searchQuery.trim() === "") {
        document.getElementById("suggestions-list").classList.add("hidden");
    } else {
        fetch(`/api/products/search?query=${searchQuery}`)
            .then(response => response.json())
            .then(data => {
                displaySuggestions(data);
            })
            .catch(error => console.error('Error:', error));
    }
});

// Ẩn danh sách gợi ý khi ô tìm kiếm mất focus
document.getElementById("search-input").addEventListener("blur", function () {
    setTimeout(() => {
        document.getElementById("suggestions-list").classList.add("hidden");
    }, 200);
});

// Hiển thị danh sách gợi ý khi tìm kiếm sản phẩm
function displaySuggestions(data) {
    const suggestionsList = document.getElementById("suggestions-list");
    suggestionsList.innerHTML = "";

    if (data.length === 0) {
        suggestionsList.classList.add("hidden");
        return;
    }

    // Tạo bảng hiển thị sản phẩm
    const table = document.createElement("table");
    table.classList.add("w-full", "border-collapse", "table-auto");

    // Tạo phần tiêu đề bảng
    const headerRow = document.createElement("tr");
    headerRow.innerHTML = `
        <th class="px-4 py-2 border">Tên sản phẩm</th>
        <th class="px-4 py-2 border">Mô tả</th>
        <th class="px-4 py-2 border">Khu vực</th>
        <th class="px-4 py-2 border">Giá (VNĐ)</th>
        <th class="px-4 py-2 border">Số lượng</th>
    `;
    table.appendChild(headerRow);

    // Duyệt qua danh sách sản phẩm và thêm vào bảng
    data.forEach(item => {
        const row = document.createElement("tr");
        row.classList.add("hover:bg-gray-100");

        row.innerHTML = `
            <td class="px-4 py-2 border">${item.productName}</td>
            <td class="px-4 py-2 border">${item.description}</td>
            <td class="px-4 py-2 border text-center">${item.zoneName}</td>
            <td class="px-4 py-2 border text-center">${Number(item.price).toLocaleString('vi-VN', {
            style: 'currency',
            currency: 'VND'
        })
        }</td>
            <td class="px-4 py-2 border text-center">${item.quantity}</td>
        `;

        row.dataset.maxQuantity = item.quantity;

        // Khi click vào sản phẩm, thêm vào danh sách "Sản phẩm đã chọn"
        row.addEventListener("click", function () {
            addProductToSelected(item);
            suggestionsList.classList.add("hidden");
        });
        table.appendChild(row);
    });

    suggestionsList.appendChild(table);
    suggestionsList.classList.remove("hidden");
}

// Thêm sản phẩm vào danh sách "Sản phẩm đã chọn"
function addProductToSelected(product) {
    const selectedProductsTableBody = document.querySelector("#selected-products");

    // Kiểm tra xem sản phẩm đã tồn tại chưa
    const existingRow = selectedProductsTableBody.querySelector(`tr[data-product-id="${product.productId}"][data-zone-id="${product.zoneId}"]`);

    if (existingRow) {
        // Nếu sản phẩm đã tồn tại, tăng số lượng thay vì thêm mới
        let quantityInput = existingRow.querySelector("input[type='number']:nth-child(2)");
        quantityInput.value = parseInt(quantityInput.value) + 1;
    } else {
        // Nếu chưa có, thêm mới sản phẩm
        const row = document.createElement("tr");
        row.classList.add("hover:bg-gray-100");

        row.dataset.productId = product.productId;
        row.dataset.zoneId = product.zoneId;
        row.dataset.maxQuantity = product.quantity;

        row.innerHTML = `
            <td class="py-2 px-4 border p-2 text-center">${selectedProductsTableBody.rows.length + 1}</td>
            <td class="py-2 px-4 border p-2">${product.productName}</td>
            <td class="py-2 px-4 border p-2">${product.description}</td>
            <td class="py-2 px-4 border p-2 text-center">${product.zoneName}</td>
            <td class="py-2 px-4 border p-2 text-center">${Number(product.price).toLocaleString('vi-VN', {
            style: 'currency',
            currency: 'VND'
        })
        }</td>
            <td class="py-2 px-4 border p-2 text-center">
                <input class="w-12 text-center rounded border-gray-300 focus:border-blue-500 outline-none transition duration-300" type="number" value="0" min="0"
                    oninput="updateOrderDetails()"/>
            </td>
            <td class="py-2 px-4 border-b text-center">
                <input class="w-12 text-center rounded border-gray-300 focus:border-blue-500 outline-none transition duration-300" type="number" value="1" min="1"
                    oninput="updateOrderDetails()"/>
            </td>
            <td class="py-2 px-4 border p-2 text-center">
                <button class="bg-red-500 text-white px-2 py-1 ml-2 rounded" onclick="removeProduct(this)">
                    <i class="fas fa-trash text-sm"></i>
                </button>
            </td>
        `;

        selectedProductsTableBody.appendChild(row);
    }

    updateOrderDetails(); // Cập nhật ngay chi tiết đơn hàng
}


// Hàm xóa sản phẩm và cập nhật lại STT
function removeProduct(button) {
    const row = button.closest("tr");
    row.remove(); // Xóa sản phẩm khỏi danh sách

    // Cập nhật lại STT sau khi xóa sản phẩm
    updateSTT();

    // Cập nhật lại chi tiết đơn hàng
    updateOrderDetails();
}

// Hàm cập nhật lại STT của tất cả sản phẩm trong danh sách "Sản phẩm đã chọn"
function updateSTT() {
    const selectedProductsTableBody = document.querySelector("#selected-products");
    let rows = selectedProductsTableBody.querySelectorAll("tr");

    rows.forEach((row, index) => {
        row.cells[0].textContent = index + 1; // Cập nhật lại STT theo thứ tự đúng
    });
}

// Cập nhật chi tiết đơn hàng
function updateOrderDetails() {
    const orderDetailsTableBody = document.querySelector("#order-details");
    orderDetailsTableBody.innerHTML = ""; // Xóa bảng cũ

    const selectedProductsTableBody = document.querySelector("#selected-products");

    selectedProductsTableBody.querySelectorAll("tr").forEach(row => {
        const productName = row.cells[1].textContent;
        let productPrice = parseInt(row.cells[4].textContent.replace(/\D/g, ""));
        let inputs = row.querySelectorAll("input");
        const maxQuantity = parseInt(row.dataset.maxQuantity); // Lấy số lượng tối đa từ thuộc tính dữ liệu
        let quantity = Math.max(1, Math.min(maxQuantity, parseInt(inputs[1].value) || 1));
        const maxDiscount = Math.floor(productPrice * 0.005);
        let discount = Math.max(0, Math.min(maxDiscount, parseInt(inputs[0].value) || 0));
        inputs[1].value = quantity;
        inputs[0].value = discount;

        const total = (productPrice - discount) * quantity;
        const finalPrice = productPrice - discount;

        // Thêm sản phẩm vào bảng "Chi tiết đơn hàng"
        const detailRow = document.createElement("tr");
        detailRow.innerHTML = `
            <td class="py-2 px-4 border-b text-center">${orderDetailsTableBody.rows.length + 1}</td>
            <td class="py-2 px-4 border-b">${productName}</td>
            <td class="py-2 px-4 border-b text-center">
                <input class="w-12 text-center rounded border-gray-300 focus:border-blue-500 outline-none transition duration-300" value="${quantity}"
                    oninput="updateOrderDetails()"/>
            </td>   
            <td class="py-2 px-4 border-b text-center total-price">${Number(finalPrice).toLocaleString('vi-VN', {
            style: 'currency',
            currency: 'VND'
        })
        }</td>            
            <td class="py-2 px-4 border-b text-center total-price">${Number(total).toLocaleString('vi-VN', {
            style: 'currency',
            currency: 'VND'
        })
        }</td>
        `;
        orderDetailsTableBody.appendChild(detailRow);
    });

    updateTotalAmount();
}

// Cập nhật tổng tiền và giảm giá
function updateTotalAmount() {

    let totalAmount = 0;
    const selectedProductsTableBody = document.querySelector("#selected-products");

    selectedProductsTableBody.querySelectorAll("tr").forEach(row => {
        const productPrice = parseInt(row.cells[4].textContent.replace(/\D/g, ""));
        const quantity = parseInt(row.querySelectorAll("input")[1].value);
        const discount = parseInt(row.querySelectorAll("input")[0].value);
        const total = (productPrice - discount) * quantity;
        totalAmount += total;
    });

    document.querySelector("#total-amount").textContent = totalAmount + " đ";

    document.getElementById("total-amount-hidden").value = totalAmount;
}

function prepareOrderData(event) {
    event.preventDefault();

    const selectedProductsTableBody = document.querySelector("#selected-products");
    let selectedProducts = [];

    selectedProductsTableBody.querySelectorAll("tr").forEach(row => {
        const productId = row.dataset.productId;
        const zoneId = row.dataset.zoneId;
        const quantity = parseInt(row.querySelectorAll("input")[1].value) || 1;

        if (productId && zoneId && quantity > 0) {
            selectedProducts.push({productId, zoneId, quantity});
        }
    });

    document.getElementById("selected-products-hidden").value = JSON.stringify(selectedProducts);

    // Gửi form sau khi cập nhật dữ liệu
    event.target.submit();
}
