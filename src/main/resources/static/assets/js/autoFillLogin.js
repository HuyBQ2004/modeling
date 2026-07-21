document.getElementById('userNameLogin').addEventListener('input', function () {
    sessionStorage.setItem('userNameLogin', this.value);
});

document.getElementById('passwordLogin').addEventListener('input', function () {
    sessionStorage.setItem('passwordLogin', this.value);
});

window.onload = function () {
    if (sessionStorage.getItem('userNameLogin')) {
        document.getElementById('userNameLogin').value = sessionStorage.getItem('userNameLogin');
    }
    if (sessionStorage.getItem('passwordLogin')) {
        document.getElementById('passwordLogin').value = sessionStorage.getItem('passwordLogin');
    }
};
