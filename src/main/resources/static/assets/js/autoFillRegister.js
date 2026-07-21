document.getElementById('userName').addEventListener('input', function () {
    sessionStorage.setItem('userName', this.value);
});

document.getElementById('email').addEventListener('input', function () {
    sessionStorage.setItem('email', this.value);
});

document.getElementById('password').addEventListener('input', function () {
    sessionStorage.setItem('password', this.value);
});

document.getElementById('confirmPassword').addEventListener('input', function () {
    sessionStorage.setItem('confirmPassword', this.value);
});


window.onload = function () {
    if (sessionStorage.getItem('userName')) {
        document.getElementById('userName').value = sessionStorage.getItem('userName');
    }
    if (sessionStorage.getItem('email')) {
        document.getElementById('email').value = sessionStorage.getItem('email');
    }
    if (sessionStorage.getItem('password')) {
        document.getElementById('password').value = sessionStorage.getItem('password');
    }
    if (sessionStorage.getItem('confirmPassword')) {
        document.getElementById('confirmPassword').value = sessionStorage.getItem('confirmPassword');
    }
};
