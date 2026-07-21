document.getElementById('customerInvoiceName').addEventListener('input', function () {
    sessionStorage.setItem('customerInvoiceName', this.value);
});

document.getElementById('customerInvoiceEmail').addEventListener('input', function () {
    sessionStorage.setItem('customerInvoiceEmail', this.value);
});

document.getElementById('customerInvoicePhone').addEventListener('input', function () {
    sessionStorage.setItem('customerInvoicePhone', this.value);
});

document.getElementById('customerInvoiceAddress').addEventListener('input', function () {
    sessionStorage.setItem('customerInvoiceAddress', this.value);
});

window.onload = function () {
    if (sessionStorage.getItem('customerInvoiceName')) {
        document.getElementById('customerInvoiceName').value = sessionStorage.getItem('customerInvoiceName');
    }
    if (sessionStorage.getItem('customerInvoiceEmail')) {
        document.getElementById('customerInvoiceEmail').value = sessionStorage.getItem('customerInvoiceEmail');
    }
    if (sessionStorage.getItem('customerInvoicePhone')) {
        document.getElementById('customerInvoicePhone').value = sessionStorage.getItem('customerInvoicePhone');
    }
    if (sessionStorage.getItem('customerInvoiceAddress')) {
        document.getElementById('customerInvoiceAddress').value = sessionStorage.getItem('customerInvoiceAddress');
    }
};
