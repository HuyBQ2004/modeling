// Lấy phần tử toggle và menu
const toggle = document.getElementById('user-dropdown-toggle');
const menu = document.getElementById('dropdown-menu');

// Thêm sự kiện click vào toggle
toggle.addEventListener('click', function (event) {
    // Ngừng sự kiện truyền ra ngoài để tránh ẩn menu khi click vào phần tử khác
    event.preventDefault();

    // Kiểm tra nếu menu đang hiển thị hay không
    if (menu.classList.contains('hidden')) {
        // Nếu menu ẩn, hiển thị nó
        menu.classList.remove('hidden');
    } else {
        // Nếu menu đang hiển thị, ẩn nó
        menu.classList.add('hidden');
    }
});

// Thêm sự kiện click ngoài menu để ẩn menu khi người dùng click ra ngoài
document.addEventListener('click', function (event) {
    if (!event.target.closest('#user-dropdown')) {
        menu.classList.add('hidden');
    }
});

