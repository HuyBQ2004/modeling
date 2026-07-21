document.getElementById("search-input-customer").addEventListener("input", function () {
    const searchQuery = document.getElementById("search-input-customer").value;

    // Nếu không có từ khóa tìm kiếm, ẩn danh sách gợi ý
    if (searchQuery.trim() === "") {
        document.getElementById("suggestions-list-customer").classList.add("hidden");
    } else {
        // Gửi yêu cầu AJAX tìm kiếm với từ khóa
        fetch(`/api/customers/search?query=${searchQuery}`)
            .then(response => response.json())
            .then(data => {
                displayCustomerSuggestions(data);
            })
            .catch(error => console.error('Error:', error));
    }
});

document.getElementById("search-input-customer").addEventListener("blur", function () {
    // Ẩn danh sách khi ô tìm kiếm mất focus
    setTimeout(() => {
        document.getElementById("suggestions-list-customer").classList.add("hidden");
    }, 200); // Để tránh trường hợp người dùng nhấn vào một mục trong dropdown
});

function displayCustomerSuggestions(data) {
    const suggestionsList = document.getElementById("suggestions-list-customer");

    // Xóa các gợi ý cũ trước khi hiển thị
    suggestionsList.innerHTML = "";

    // Nếu không có kết quả tìm kiếm
    if (data.length === 0) {
        suggestionsList.classList.add("hidden");
        return;
    }

    // Tạo bảng để hiển thị danh sách khách hàng
    const table = document.createElement("table");
    table.classList.add("w-full", "border-collapse", "table-auto");

    // Tạo phần tiêu đề bảng
    const headerRow = document.createElement("tr");
    headerRow.innerHTML = `
        <th class="px-4 py-2 border">Tên khách hàng</th>
        <th class="px-4 py-2 border">SĐT</th>
        <th class="px-4 py-2 border">Email</th>
        <th class="px-4 py-2 border">Địa chỉ</th>
    `;
    table.appendChild(headerRow);

    // Hiển thị các khách hàng trong bảng
    data.forEach(item => {
        const row = document.createElement("tr");
        row.classList.add("hover:bg-gray-100");

        row.innerHTML = `
            <td class="px-4 py-2 border">${item.name}</td>
            <td class="px-4 py-2 border text-center">${item.phone}</td>
            <td class="px-4 py-2 border text-center">${item.email}</td>
            <td class="px-4 py-2 border text-center">${item.address}</td>
        `;

        // Thêm sự kiện click để chọn khách hàng
        row.addEventListener("click", function () {
            document.querySelector('input[placeholder="Tên khách hàng"]').value = item.name;
            document.querySelector('input[placeholder="Địa chỉ"]').value = item.address;
            document.querySelector('input[placeholder="Số điện thoại"]').value = item.phone;
            document.querySelector('input[placeholder="Email"]').value = item.email;

            document.getElementById("customer-id").value = item.id;

            suggestionsList.classList.add("hidden"); // Ẩn danh sách gợi ý sau khi chọn khách hàng
        });

        table.appendChild(row);
    });

    // Thêm bảng vào danh sách gợi ý
    suggestionsList.appendChild(table);
    suggestionsList.classList.remove("hidden");
}
