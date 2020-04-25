<template>
    <div class="user">
        <img class="avatar" :src="user.avatar" />
        <div class="username">
            <span>{{ user.username | nick }}</span>
            <span class="tag">{{ user.username | tag }}</span>
        </div>
        <span class="email" v-if="user.email">{{ user.email }}</span>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-user',

        computed: mapState({ user: state => state.auth.user }),
        filters: {
            nick(s) {
                return s.substring(0, s.indexOf('#'));
            },
            tag(s) {
                return s.substring(s.indexOf('#'));
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

    .user {
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .avatar {
        border-radius: 50%;

        width: 128px;
        height: 128px;
    }

    .username {
        display: flex;
        margin-top: 15px;

        @include lato(bold);
        font-size: 30px;

        .tag {
            font-style: italic;
            font-weight: normal;
        }
    }

    .email {
        font-size: 18px;
        font-style: italic;
    }
</style>