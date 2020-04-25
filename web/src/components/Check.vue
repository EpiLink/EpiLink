<template>
    <div class="check-container">
        <div class="check">
        </div>
    </div>
</template>

<script>
    export default {
        name: 'link-check'
    }
</script>

<style lang="scss">
    @import '../styles/vars';

    .check-container {
        width: 200px;
        height: 200px;

        border-radius: 50%;

        background-color: lighten($primary-color, 4.5%);
    }

    .check {
        width: 100px;
        height: 50px;

        margin-top: 107.5px;
        margin-left: 37.5px;

        border: solid 15px white;
        border-top: none;
        border-right: none;
        border-radius: 4px;

        transform: rotate(-45deg);
        transform-origin: top left;

        animation: check 0.5s 0.5s ease 1 both;
    }

    @keyframes check {
        0% {
            opacity: 0;
            width: 0;
            height: 0;
        }

        50% {
            opacity: 1;
            width: 0;
            height: 50px;
        }

        100% {
            width: 100px;
        }
    }
</style>