document.getElementById('storeName').addEventListener('input', function () {
    sessionStorage.setItem('storeName', this.value);
});

document.getElementById('storeEmail').addEventListener('input', function () {
    sessionStorage.setItem('storeEmail', this.value);
});

document.getElementById('storePhone').addEventListener('input', function () {
    sessionStorage.setItem('storePhone', this.value);
});

document.getElementById('storeAddress').addEventListener('input', function () {
    sessionStorage.setItem('storeAddress', this.value);
});

window.onload = function () {
    if (sessionStorage.getItem('storeName')) {
        document.getElementById('storeName').value = sessionStorage.getItem('storeName');
    }
    if (sessionStorage.getItem('storeEmail')) {
        document.getElementById('storeEmail').value = sessionStorage.getItem('storeEmail');
    }
    if (sessionStorage.getItem('storePhone')) {
        document.getElementById('storePhone').value = sessionStorage.getItem('storePhone');
    }
    if (sessionStorage.getItem('storeAddress')) {
        document.getElementById('storeAddress').value = sessionStorage.getItem('storeAddress');
    }
};
