package com.lucknow.bdd.steps

import io.cucumber.java.Before

class Hooks {

    @Before
    fun resetWorld() {
        BddWorld.reset()
    }
}
