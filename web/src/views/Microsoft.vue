<template>
    <div id="microsoft" v-if="profile">
        <img id="avatar" :src="profile.avatar" />
        <div id="username">
            <span>{{ profile.username | nick }}</span>
            <span id="tag">{{ profile.username | tag }}</span>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'link-microsoft',

        beforeMount() {
            if (!this.$store.state.user) {
                this.$router.push({ name: 'home' });
            }
        },
        computed: {
            profile() {
                return this.$store.state.user;
            }
        },
        filters: {
            nick(username) {
                return username.substring(0, username.indexOf('#'));
            },
            tag(username) {
                return username.substring(username.indexOf('#'));
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

    #microsoft {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
    }

    #avatar {
        border-radius: 50%;

        width: 128px;
        height: 128px;
    }

    #username {
        display: flex;
        margin-top: 15px;

        @include lato(bold);
        font-size: 30px;

        #tag {
            font-style: italic;
            font-weight: normal;
        }
    }
</style>